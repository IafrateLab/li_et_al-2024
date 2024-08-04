
/* Script to detect nuclei using DAPI channel and iteratively detect other markers. Cell detections from each
 * iteration will be given the same class as the name of the channel used as the primary segmentation channel
 * Author: Dawn Mitchell, modified from Cellpose extension script templates
 */
 
import qupath.ext.biop.cellpose.Cellpose2D
import qupath.ext.biop.cellpose.CellposeSetup
import qupath.lib.images.ImageData
import qupath.lib.images.servers.ImageServer
import qupath.lib.gui.logging.LogManager
import qupath.lib.common.GeneralTools

def imageData = getCurrentImageData()
def imageServer = imageData.getServer()
def entry = getProjectEntry()

// Get name of results folder from CLI 
if (args.size() == 0) {
    results = "results"
} else {
    results = "results_" + args[0]
}

def imageName = GeneralTools.stripExtension(entry.getImageName())

// Create log directory and Cellpose log file for this image
def logFilepath = buildFilePath(PROJECT_BASE_DIR, results, imageName, "logs")
mkdirs(logFilepath)

def logFile = new File(logFilepath, "cellpose.log")
LogManager.logToFile(logFile)

// Initialize Cellpose
def setup = CellposeSetup.getInstance()
setup.setCellposePytonPath('C:/Users/dy492/.conda/envs/cellpose/python.exe')

def numChannels = imageServer.nChannels()

// Sometimes channel names include the index, better to call the name in the image data
def dapiChannelName = imageServer.getChannel(0).getName()

// List to store detections at each iteration
def detectionList = []

def nucMarkers = ["FoxP3", "p63", "NFIB", "cMyb", "SOX2", "Ki67", "MYB"]

// Make GeoJSON directory
def geojsonDir = buildFilePath(PROJECT_BASE_DIR, results, imageName, "geojson", "all_segmentations")
mkdirs(geojsonDir)

// Create full image annotation
def pathObject = createFullImageAnnotation(true)

// Clear any detections currently in the annotations
clearDetections()
    
// Iterate over all other channels for 2-channel segmentation
for (int channel = 0; channel < numChannels; channel++) {
    
    def segChannelName = imageServer.getChannel(channel).getName()

    // Skip over autofluorescence channels and nuclear markers
    if (!(channel == 1) && !(channel == 2) && !nucMarkers.any { segChannelName.contains(it)}) {
    
        def pathMarkerModel = "cyto3"
        def secondCellposeChannel = 2
        def diam = 25.0

        
        if (segChannelName.contains("DAPI")) {
                secondCellposeChannel = 0
                diam = 13.0
        }
        
        def cellposeMarker = Cellpose2D.builder( pathMarkerModel )
            .pixelSize( 0.5 )                              // Resolution for detection in um
            .channels(segChannelName, dapiChannelName)     // Select detection channel(s)
            .setOverlap(75)
            .diameter(diam)
            .tileSize(2048)                                // If your GPU can take it, make larger tiles to process fewer of them. Useful for Omnipose
            .cellposeChannels(1, secondCellposeChannel)    // Overwrites the logic of this plugin with these two values. These will be sent directly to --chan and --chan2
            .classify(segChannelName)                      // PathClass to give newly created objects
            .simplify(0)                                   // Simplification 1.6 by default, set to 0 to get the cellpose masks as precisely as possible
            .build()
    
        // Segment marker with DAPI and store detections
        cellposeMarker.detectObjects(imageData, [pathObject])
        detections = getDetectionObjects()
        detectionList.add(detections)    
        
        def annoID = pathObject.getID().toString()
        
        println annoID + " " + segChannelName + " segmentation complete"
        
        def geosjsonFilename = imageName + "_" + annoID + "_" + segChannelName.replace(" ", "") + ".geojson"
        
        def geojsonFilepath = buildFilePath(geojsonDir, geosjsonFilename)
        
        exportObjectsToGeoJson(detections, geojsonFilepath, "FEATURE_COLLECTION")
        
        println annoID + " " + segChannelName + " segmentation masks saved as GeoJSON"
    }
    
}

// Clear current detections
clearDetections()

for (item in detectionList) {
    addObjects(item)
}

// Update hierarchy
fireHierarchyUpdate()

println "All channel segmentation complete"


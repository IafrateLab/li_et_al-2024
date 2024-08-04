/* Takes all previously detected marker segmentations and combines them into cell detection objects based on proximity to DAPI segmentations.
 * Authors: Dawn Mitchell and Stefan Kaluziak
 */

import groovyx.gpars.GParsPool
import java.awt.geom.Point2D
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import qupath.lib.analysis.features.ObjectMeasurements
import qupath.lib.common.GeneralTools
import qupath.lib.gui.tools.MeasurementExporter
import qupath.lib.objects.PathCellObject
import qupath.lib.roi.RoiTools

// Get current image project entry and image name
def entry = getProjectEntry()
def imageName = GeneralTools.stripExtension(entry.getImageName())

// Get name of results folder from CLI 
if (args.size() == 0) {
    results = "results"
} else {
    results = "results_" + args[0]
}


// Create log directory and combine detections log file for this image
def logFilepath = buildFilePath(PROJECT_BASE_DIR, results, imageName, "logs")
mkdirs(logFilepath)

LogManager.setInfo()

def logFile = new File(logFilepath, "combine.log")
LogManager.logToFile(logFile)

// Arguments that will be used for cell intensity and shape measurements
def server = getCurrentServer()
def cal = server.getPixelCalibration()
def downsample = 1.0

// Remove autofluorescence detections if they exist
def toRemove = getDetectionObjects().findAll{it.getPathClass() == getPathClass('Cy5') || it.getPathClass() == getPathClass('TRITC')}
removeObjects(toRemove, false)

// Remove autofluorescence detections if they exist
def removeAF = getDetectionObjects().findAll{it.getPathClass().getName().contains('AF555') || it.getPathClass().getName().contains('AF647')}
removeObjects(removeAF, false)

// Create collection of nuclei and collection of marker detections
def nucDetections = getDetectionObjects().findAll { it.getPathClass().getName().contains("DAPI") }
def markerDetections = getDetectionObjects().findAll { !it.getPathClass().getName().contains("DAPI") }

// Sort nuclei detections by centroid X, then by centroid Y
nucDetections.sort { a, b ->
    def result = a.getROI().getCentroidX() <=> b.getROI().getCentroidX()
    if (result == 0) {
        result = a.getROI().getCentroidY() <=> b.getROI().getCentroidY()
    }
    result
}

// Sort marker detections by centroid X, then by centroid Y
markerDetections.sort { a, b ->
    def result = a.getROI().getCentroidX() <=> b.getROI().getCentroidX()
    if (result == 0) {
        result = a.getROI().getCentroidY() <=> b.getROI().getCentroidY()
    }
    result
}

def totalNucDetections = nucDetections.size()
def progress = new AtomicInteger(0)
def cells = new ConcurrentLinkedQueue<PathCellObject>()

Queue<PathObject> sortedNucDetections = new LinkedList<>(nucDetections)

// Create sublists of nuclear detections and marker detections to parallelize matching 
def sublistMap = new ConcurrentHashMap<String, List>()

int numLists = 50

if (nucDetections.size() % 49  == 0) {
    numLists = 49
}

def nucSublistSize = nucDetections.size().intdiv(49)

def lastIndex = nucSublistSize - 1

for (int i = 0; i < numLists; i++) {
    def tmpNucList = []
    
    if (sortedNucDetections.size() >= nucSublistSize) {
        for (int j = 0; j < nucSublistSize; j++) {
            tmpNucList.add(sortedNucDetections.pop())
        }
    } else {
        while (sortedNucDetections.peek() != null) {
            tmpNucList.add(sortedNucDetections.pop())
        }
        lastIndex = tmpNucList.size() - 1   
    }
    
    def tmpNucListMaxX = tmpNucList[lastIndex].getROI().getCentroidX()
    def tmpNucListMaxY = tmpNucList[lastIndex].getROI().getCentroidY()
    def tmpNucListMinX = tmpNucList[0].getROI().getCentroidX()
    def tmpNucListMinY = tmpNucList[0].getROI().getCentroidY()

    
    def tmpMarkerList = []

    for (marker in markerDetections) {
        
        def markCentroidX = marker.getROI().getCentroidX()
        def markCentroidY = marker.getROI().getCentroidY()

        if (markCentroidX < tmpNucListMinX){
            continue
        } else if (markCentroidX >= tmpNucListMinX && markCentroidX < tmpNucListMaxX) {
            tmpMarkerList.add(marker) 
        } else if (markCentroidX == tmpNucListMaxX && markCentroidY < tmpNucListMaxY) {
            tmpMarkerList.add(marker)
        } else if (markCentroidX > tmpNucListMaxX && markCentroidY > tmpNucListMaxY && Point2D.distance(tmpNucListMaxX, tmpNucListMaxY, markCentroidX, markCentroidY) <= 130) {
            tmpMarkerList.add(marker)
        } else {
            break
        }
        
    }
    
    sublistMap["sub_${i + 1}"] = [tmpNucList, tmpMarkerList]
}


GParsPool.withPool(50) { // Limit the number of threads to 50
    sublistMap.eachParallel { sublist, nucMarkers ->
        def sublistMarkers = []
        
        for (item in nucMarkers[1]) {
            sublistMarkers.add(item)
        }
        
        for (nuc in nucMarkers[0]) {
            def nucCentroidX = nuc.getROI().getCentroidX()
            def nucCentroidY = nuc.getROI().getCentroidY()
            def markerList = []
            def markersToRemove = []
            
            for (markerDetection in sublistMarkers) {
                def markerCentroidX = markerDetection.getROI().getCentroidX()
                def markerCentroidY = markerDetection.getROI().getCentroidY()
                
                // If marker centroid is >130 pixels away, ignore for this nucleus
                if (Point2D.distance(nucCentroidX, nucCentroidY, markerCentroidX, markerCentroidY) > 130 ) {
                    continue
                }
                
                // If marker ROI contains nucleus centroid, add to list of markers for this nucleus 
                if (markerDetection.getROI().contains(nucCentroidX, nucCentroidY)) {
                    markerList.add(markerDetection)
                    markersToRemove.add(markerDetection)
                }
                
            }
            
            
            // Add all marker ROIs to acheive final cell outline
            if (markerList.size() != 0) {
                def fullROI = markerList[0].getROI()
            
                for (marker in markerList) {
                    fullROI = RoiTools.combineROIs(fullROI, marker.getROI(), RoiTools.CombineOp.ADD)
                }
                
                synchronized (cells) {
                    cells.add(PathObjects.createCellObject(fullROI, nuc.getROI(), getPathClass("Combined"), null))
                }
            
            }
    
    
            int currentProgress = progress.incrementAndGet()
            synchronized (System.out) {
                println "${currentProgress} of ${totalNucDetections}"
            }
            
            sublistMarkers.removeAll(markersToRemove)    
        }
        
    }
        
}

println "Cell creation complete"

clearDetections()
addObjects(cells)

// Get shape and intensity measurements for cell detections
def measurements = ObjectMeasurements.Measurements.values() as List
def compartments = ObjectMeasurements.Compartments.values() as List
def shape = ObjectMeasurements.ShapeFeatures.values() as List
def allCells = getCellObjects()

GParsPool.withPool(10) {
    allCells.eachParallel { cell ->
        ObjectMeasurements.addIntensityMeasurements(server, cell, downsample, measurements, compartments)
        ObjectMeasurements.addCellShapeMeasurements(cell, cal, shape)
    }
}

// Get the list of all images in the current project
def entryList = []
entryList << getProjectEntry()

def imageData = entry.readImageData()

// Save imageData to add in hierarchy info
fireHierarchyUpdate()
resolveHierarchy()
entry.saveImageData(getCurrentImageData())

// Save combined cell detections as GeoJSON
def geojsonPath = buildFilePath(PROJECT_BASE_DIR, results, imageName, "geojson")
mkdirs(geojsonPath)

def cellsPath = buildFilePath(geojsonPath, imageName + "_cells.geojson")

exportObjectsToGeoJson(allCells, cellsPath, "FEATURE_COLLECTION")

println 'Done!'


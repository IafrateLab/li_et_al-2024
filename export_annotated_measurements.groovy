/* Exports a measurement CSV for annotations already present in a QuPath image.
 * Assumes that cell detections with measurements and annotation objects are already present.
 */

import qupath.lib.common.GeneralTools
import qupath.lib.gui.tools.MeasurementExporter
import qupath.lib.objects.PathCellObject

// Get name of results folder from CLI 
if (args.size() == 0) {
    results = "results"
} else {
    results = "results_" + args[0]
}

// Get the list of all images in the current project
def entry = getProjectEntry()
def entryList = []
entryList << entry

def imageData = entry.readImageData()
def imageName = GeneralTools.stripExtension(entry.getImageName())

// Save imageData to add in hierarchy info
fireHierarchyUpdate()
resolveHierarchy()
entry.saveImageData(getCurrentImageData())

// Export file specifications
def separator = ","

def exportType = PathCellObject.class

def csvName = imageName +'_CellMeasurements.csv'

def outputPath = buildFilePath(PROJECT_BASE_DIR, results, imageName, "csv")
mkdirs(outputPath)

def outputFile = new File(buildFilePath(outputPath, csvName))

// Create the measurementExporter and start the export
def exporter  = new MeasurementExporter()
                   .imageList(entryList)            // Images from which measurements will be exported
                   .separator(separator)                 // Character that separates values
                   .exportType(exportType)               // Type of objects to export
                   .exportMeasurements(outputFile)        // Start the export process

println 'Done!'
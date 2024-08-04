/* For a given project image, finds the corresponding DICOM annotation directory and 
 * imports each annotation GeoJSON file, then exports a measurement CSV indicating
 * which cell detections belong to which annotation.
 * Author: Dawn Mitchell
 */


import qupath.lib.analysis.features.ObjectMeasurements
import qupath.lib.common.GeneralTools
import qupath.lib.gui.tools.MeasurementExporter
import qupath.lib.objects.PathCellObject

// dictionary of images and corresponding name for each image's annotation directory
studyIDs = [
    "20230818_154652_4_gH2ucf_14plex_ACC_ACC_35a_bg_sub.ome.tiff": "ACC_35",
    "20220922_154855_2_oJDyMD_Lunaphore_10plex_test_ACC_3_bg_sub.ome.tiff": "ACC_3",
    "20230613_132326_2_IJ1YrJ_14plex_ACC_SqCC_3_bg_sub.ome.tiff": "SqCC_3",
    "20230802_144116_1_mUA5Ro_14plex_ACC_SqCC_7a_bg_sub.ome.tiff": "SqCC_7",
    "20230602_162434_2_uYO6ZG_14plex_ACC_ACC_24_bg_sub.ome.tiff": "ACC_24",
    "20221020_164730_1_7bIFnl_10plex_standard_ACC_1a_bg_sub.ome.tiff": "ACC_1",
    "20220927_195322_4_2abuQn_Lunaphore_10plex_test_ACC_4_bg_sub.ome.tiff": "ACC_4",
    "20220927_195322_3_WHthyv_Lunaphore_10plex_test_ACC_5_bg_sub.ome.tiff": "ACC_5",
    "20220927_195322_2_0zCUW2_Lunaphore_10plex_test_ACC_6_bg_sub.ome.tiff": "ACC_6",
    "20220928_194850_4_fVzp7i_Lunaphore_10plex_test_ACC_7_bg_sub.ome.tiff": "ACC_7",
    "20221020_164731_4_gTOt8N_10plex_standard_ACC_13_bg_sub.ome.tiff": "ACC_13",
    "20221019_183413_1_jgqQpt_10plex_standard_ACC_16_bg_sub.ome.tiff":	"ACC_16",
    "20220929_193742_3_iYhvbB_Lunaphore_10plex_test_ACC_11_bg_sub.ome.tiff": "ACC_17",
    "20221019_183413_2_lGErjd_10plex_standard_ACC_19_bg_sub.ome.tiff": "ACC_19",
    "20220928_194850_2_6ctBl6_Lunaphore_10plex_test_ACC_9_bg_sub.ome.tiff": "ACC_20",
    "20221020_164731_2_dtzSEd_10plex_standard_ACC_21_bg_sub.ome.tiff": "ACC_21",
    "20230602_162434_4_x8mWT0_14plex_ACC_ACC_22_bg_sub.ome.tiff": "ACC_22",
    "20230602_162434_3_Qq5J18_14plex_ACC_ACC_23_bg_sub.ome.tiff": "ACC_23",
    "20230603_160955_4_uOKA0q_14plex_ACC_ACC_25_bg_sub.ome.tiff": "ACC_25",
    "20230603_160955_3_O6dxYy_14plex_ACC_ACC_26_bg_sub.ome.tiff": "ACC_26",
    "20230603_160955_1_LWEXeE_14plex_ACC_ACC_27_bg_sub.ome.tiff": "ACC_27",
    "20230605_132705_1_qVOOjD_14plex_ACC_ACC_30_bg_sub.ome.tiff": "ACC_30",
    "20230613_132326_1_Fjd7xZ_14plex_ACC_ACC_33_bg_sub.ome.tiff": "ACC_33",
    "20230721_194818_3_KJ5bUN_14_plex_pdl2_swap_bm_26a_bg_sub.ome.tiff": "brain_met_26",
    "20230406_192301_1_mASHtv_14_plex_pdl2_swap_brain_met_30_bg_sub.ome.tiff": "brain_met_30",
    "20230614_182350_2_h6VGRw_14plex_ACC_SqCC_5a_bg_sub.ome.tiff": "SqCC_5",
    "20230526_230737_3_jpRez3_14plex_ACC_scca_bg_sub.ome.tiff": "SqCC_2"
]

// Remove any existing annotation objects
removeObjects(getAnnotationObjects(), true)

def entry = getProjectEntry()
def imageName = GeneralTools.stripExtension(entry.getImageName())

// Default annotation and results directories
def annotDir = "A:/annotations"
def resultsDir = "results"

if (args.size() == 2) {
    annotDir = args[0]
    resultsDir = args[1]
}

// build paths to annotations for the given image and its results directory
def annotPath = buildFilePath(annotDir, studyIDs[entry.getImageName()])
def resultsPath = buildFilePath(PROJECT_BASE_DIR, resultsDir, imageName, "csv", studyIDs[entry.getImageName()] + "-annotated")
mkdirs(resultsPath)

// Loop through all annotation GeoJSONs for this image, making separate CSVs for each file
new File(annotPath).eachFile() {file -> 
        
    def imported = importObjectsFromFile(file.toString())

    if (imported) {
        
        // Remove any annotations with area 0 (likely accidents)
        for (obj in getAnnotationObjects()) {
            if (obj.getROI().getArea() == 0) {
                println "Removing ${obj.getName()}"
                removeObject(obj, true)
            }
        }

        // Save imageData to add in hierarchy info
        fireHierarchyUpdate()
        resolveHierarchy()
        entry.saveImageData(getCurrentImageData())

        def annotType = GeneralTools.stripExtension(file.toString().split('_')[-1])

        // Export file specifications
        def separator = ","

        def exportType = PathCellObject.class

        def csvName = imageName + "_" + annotType +'.csv'

        def outputFile = new File(buildFilePath(resultsPath, csvName))

        def entryList = []
        entryList << entry

        // Create the measurementExporter and start the export
        def exporter  = new MeasurementExporter()
                          .imageList(entryList)            // Images from which measurements will be exported
                          .separator(separator)                 // Character that separates values
                          .exportType(exportType)               // Type of objects to export
                          .exportMeasurements(outputFile)        // Start the export process

        // Remove all annotations
        def annotsRemove = getAnnotationObjects().findAll { it.getName() }
        removeObjects(annotsRemove, true)
        
        fireHierarchyUpdate()

    }

}
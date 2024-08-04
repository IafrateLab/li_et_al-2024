# Immune Landscape of Adenoid Cystic Carcinoma Reveals Reversible Downregulation of HLA class I

Repository that contains code considered during peer-review process. 

## Data Husbandry and Raw Image Preprocessing 
- [`extract_rois.py`](extract_rois.py): Used to download the DICOM-based annotations from the Google Cloud DICOM store and convert them into GeoJSON objects to be used in QuPath.
- [`iterate_cellpose_over_annotations.groovy`](iterate_cellpose_over_annotations.groovy): The name of this script does not match its current role, which is to conduct cell segmentation on a whole-image annotation (it was previously necessary to divide the image into sections, hence the name). DAPI and all marker channels (not autofluorescence or Alexa fluor channels) are segmented separately, with DAPI used as the secondary channel for the marker channels. Segmentation masks are saved as GeoJSON objects.
- [`combine_detections.groovy`](combine_detections.groovy): Takes all previously detected marker segmentations and combines them into cell detection objects based on proximity to DAPI segmentations.
- [`import_and_measure.groovy`](import_and_measure.groovy): Imports annotations into QuPath for the corresponding image and sequentially produces cell intensity summary CSVs indicating the annotation type for each cell.
- [`export_annotated_measurements.groovy`](export_annotated_measurements.groovy): Exports a measurement CSV for annotations already present in a QuPath image. Assumes that cell detections with measurements and annotation objects are already present. Used to generate measurement CSVs for the normal samples, which were annotated directly in QuPath.

## Code for Image Analysis
- [`generate_rectangle_annotation.groovy`](generate_rectangle_annotation.groovy): Used to create a rectangle annotation at given coordinates. Was used to create images corresponding to centroid plots for the ACC project.
- [`recalculate_detection_measurements.groovy`](recalculate_detection_measurements.groovy): A convenient script for removing measurements from currently-loaded cell detections and recalculating them. Useful for regenerating summary statistics that can be used to compare two versions of the same image to detect differences (e.g. confirming that pixel values did not change when writing to a new TIFF file).
- [`qupath_segmentation.sh`](qupath_segmentation.sh): This script was mainly used to conduct marker segmentation and combine detections headlessly.

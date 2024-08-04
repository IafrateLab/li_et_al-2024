#!/usr/bin/bash

export PROJECTS=('A:\\brain-mets\\qupath-projects\\brain-mets-3\\project.qpproj')

export SCRIPT_DIR='C:\\Users\\dy492\\Pictures\\Lunaphore\\shared_scripts\\lunaphore\\'

printf -v RESULTS '%(%Y%m%d%H%M%S)T' -1

for i in "${!PROJECTS[@]}"; do

    echo "Running Cellpose segmentation on all channels"
    ./'QuPath-0.5.1 (console).exe' script -p ${PROJECTS[i]} --args ${RESULTS} --save "${SCRIPT_DIR}iterate_cellpose_over_annotations.groovy"

    echo "Combining detections"
    ./'QuPath-0.5.1 (console).exe' script -p ${PROJECTS[i]} --args ${RESULTS} --save "${SCRIPT_DIR}combine_detections.groovy"

    # echo "Creating annotated CSVs"
    ./'QuPath-0.5.1 (console).exe' script -p ${PROJECTS[i]} --args ${RESULTS} --save "${SCRIPT_DIR}import_and_measure.groovy"

    #  echo "Recalculate detection measurements"
    # ./'QuPath-0.5.1 (console).exe' script -p ${PROJECTS[i]} --args ${RESULTS} --save "${SCRIPT_DIR}recalculate_detection_measurements.groovy"   

    # echo "Creating annotated CSVs for normals"
    # ./'QuPath-0.5.1 (console).exe' script -p ${PROJECTS[i]} --args ${RESULTS} --save "${SCRIPT_DIR}export_annotated_measurements.groovy"

done

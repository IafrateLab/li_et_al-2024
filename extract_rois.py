# Takes a CSV containing DICOM study IDs, slide minimum X and Y coordinates, study instance UIDs and container identifiers
# and downloads the corresponding ROIs of each annotation code for a container. The ROIs are then converted into Shapely objects,
# which are then converted to GeoJSON to be imported into QuPath.
# Authors: Adam von Paternos and Dawn Mitchell

import argparse
import json
import logging
import os

import highdicom as hd
import pandas as pd

from dicomweb_client import DICOMClient
from dicomweb_client.api import DICOMwebClient
from dicomweb_client.ext.gcp.session_utils import create_session_from_gcp_credentials
from dicomweb_client.ext.gcp.uri import GoogleCloudHealthcareURL
from pydicom.dataset import Dataset
from pydicom.sr.coding import Code
from shapely.geometry import Polygon
from shapely import get_coordinates


logger = logging.getLogger(__name__)

# DICOM ROI codes of interest
codes = {
    "399721002": "Tumor infiltration by lymphocytes present",
    "47973001": "Artifact",
    "39577004": "Tumor cells of uncertain behavior",
    "108369006": "Tumor"
}

def main():

    cli_args = get_args()

    # Data type dictionary for interpreting CSV columns
    dtype_dict = {
        "StudyID": str,
        "x_min": float,
        "y_min": float,
        "StudyInstanceID": str,
        "ContainerIdentifier": str
    }

    # Create output path
    annotations_path = os.path.join(cli_args.output, "annotations")

    if not os.path.exists(annotations_path):
        os.mkdir(annotations_path)

    # Read CSV of DICOM study info
    study_info = pd.read_csv(cli_args.spreadsheet, dtype=dtype_dict)

    # Set up Google Cloud session to download ROIs
    url = GoogleCloudHealthcareURL(
    project_id="mgh-comet-1639084656",
    location="us-east4",
    dataset_id="lunaphore",
    dicom_store_id="images",
    )

    remote_client = DICOMwebClient(
        url=str(url), session=create_session_from_gcp_credentials()
    )

    # Download and convert annotations for each sample
    for idx, row in study_info.iterrows():

        # Create separate directories for each row (study ID)
        study_id_path = os.path.join(annotations_path, row["StudyID"])

        # Some samples have 2 containers with annotations
        if os.path.exists(study_id_path):
            study_id_path = os.path.join(annotations_path, row["StudyID"] + "_1")

        os.mkdir(study_id_path)

        # Iterate over all codes to get ROIs of each code type
        for code, name in codes.items():
            rois = get_tumor_contours(remote_client, row["StudyInstanceUID"], row["ContainerIdentifier"], code)

            gj = create_geojson(rois, code, row["x_min"], row["y_min"])

            gj_filename = f'{row["StudyID"]}_{row["StudyInstanceUID"]}_{row["ContainerIdentifier"]}_{name.lower().replace(" ", "-")}.geojson'

            gj_path = os.path.join(study_id_path, gj_filename)

            # Write GeoJSON file only if ROIs were found
            if gj:
                with open(gj_path, "w") as f:
                    json.dump(gj, f)

            print(f'{row["StudyID"]} {codes[code]} GeoJSON for container {row["ContainerIdentifier"]} complete')


def _get_planar_roi_annotations(
    client: DICOMClient,
    study_instance_uid: str,
    container_identifier: str,
    code: Code,
) -> list[hd.sr.PlanarROIMeasurementsAndQualitativeEvaluations]:
    """
    Uses a remote web client to get the corresponding DICOM SR documents for a given study, container ID, and code and returns them in a list.

    Parameters
    ----------
    client: DICOMClient
        Google Cloud web client to access DICOM store
    study_instance_uid: str
        UID corresponding to a given sample
    container_identifier: str
        ID for the container within which to search for ROIs
    code: Code
        ROI code to search for in the given container

    Returns
    -------
    list[hd.sr.PlanarROIMeasurementsAndQualitativeEvaluations] 
        list of highdicom object corresponding to the SR documents representing the ROIs for a given sample
    """
    
    matched_instances = [
        Dataset.from_json(ds)
        for ds in client.search_for_instances(
            study_instance_uid=study_instance_uid,
            search_filters={"Modality": "SR"},
        )
    ]
    matched_documents = [
        hd.sr.Comprehensive3DSR.from_dataset(
            client.retrieve_instance(
                study_instance_uid=study_instance_uid,
                series_instance_uid=instance.SeriesInstanceUID,
                sop_instance_uid=instance.SOPInstanceUID,
            )
        )
        for instance in matched_instances
    ]
    roi_groups = []
    for document in matched_documents:
        spec_container_id = [
            i
            for i in document.ContentSequence
            if i.ConceptNameCodeSequence[0].CodeMeaning
            == "Specimen Container Identifier"
        ]
        if len(spec_container_id) > 1:
            logger.warning(
                "Found more than one container identifier in SR document."
            )
        elif len(spec_container_id) == 1:
            if spec_container_id[0].TextValue == container_identifier:
                roi_groups.extend(
                    document.content.get_planar_roi_measurement_groups(
                        finding_type=code,
                        graphic_type=hd.sr.GraphicTypeValues3D.POLYGON,
                    )
                )
            else:
                logger.debug(
                    f"Omitting SR document matching for different container identifier '{spec_container_id[0].TextValue}'"
                )
    return roi_groups


def _get_3dsrs_annotation_contours_by_finding_type(
    client: DICOMClient,
    study_instance_uid: str,
    container_identifier: str,
    code: Code,
) -> list[Polygon]:
    """
    Gets corresponding SR documents for a given study, container ID, and code and returns a list of 
    Shapely polygons corresponding to the ROIs
    Parameters
    ----------
    client: DICOMClient
        Google Cloud web client to access DICOM store
    study_instance_uid: str
        UID corresponding to a given sample
    container_identifier: str
        ID for the container within which to search for ROIs
    code: Code
        ROI code to search for in the given container

    Returns
    -------
    list[Polygon] 
        list of Shapely polygons corresponding to ROIs of type code
    """

    roi_measurments_groups = _get_planar_roi_annotations(
        client, study_instance_uid, container_identifier, code
    )

    rois = []

    # Convert ROIs to Shapely polygons and add to list
    for group in roi_measurments_groups:
        if group.roi is not None:
            poly = Polygon(group.roi.value)
            if poly in rois:
                logger.warning("Omitting duplicate ROI.")
            else:
                rois.append(poly)
    return rois

def get_args():
    """
    Get arguments from CLI

    Returns
    -------
    argparse object containing CLI args
    """

    parser = argparse.ArgumentParser(description="Enter the spreadsheet and output paths.")

    # Spreadsheet of study information
    parser.add_argument("-s",
                        "--spreadsheet",
                        dest="spreadsheet",
                        help="Path to spreadsheet containing StudyInstanceUIDs, ContainerIdentifiers, and x_min and y_min")
    
    # Path for resulting annotation folder for the sample list
    parser.add_argument("-o",
                        "--output",
                        dest="output",
                        help="Path to create annotation folder")

    return parser.parse_args()


def get_tumor_contours(
    client: DICOMClient,
    study_instance_uid: str,
    container_identifier: str,
    code: str,
) -> list[Polygon]:
    """
    Get the contours of image regions containing tumor.

    Parameters
    ----------
    client: dicomweb_client.DICOMClient
        DICOM client
    study_instance_uid: str
        UID of the DICOM study

    Returns
    -------
    List[shapely.geometry.Polygon]
        Contours

    """
    return _get_3dsrs_annotation_contours_by_finding_type(
        client,
        study_instance_uid,
        container_identifier,
        Code(code, "SCT", codes[code]),
    )

def create_geojson(
    polygons: list,
    code: str,
    x_min: float,
    y_min: float,
) -> str:
    """
    Creates a GeoJSON file corresponding to a list of Shapely polygons.

    Parameters
    ----------
    polygons: List[shapely.geometry.Polygon]
        list of Shapely polygons indicating regions of interest
    code: str
        code corresponding to the DICOM ROI code for this list of polygons
    x_min: float
        the minimum X-coordinate of the sample slide in mm
    y_min: float
        the minimum Y-coordinate of the sample slide in mm

    Returns
    -------
    str representing a properly-formatted GeoJSON file
    
    """
    pixel_spacing = 0.00023
    x_0 = x_min
    y_0 = y_min

    # list of all "features" (each feature is an annotation)
    features = []

    for idx, polygon in enumerate(polygons):

        # get coordinates in millimeters
        mm_coords = get_coordinates(polygon)

        # x and y coordinates are flipped
        pixel_coords = [(int((y - y_0) / pixel_spacing), int((x - x_0) / pixel_spacing)) for x, y in mm_coords]

        feature = {
            "type": "Feature",
            "geometry": {
                "type": "Polygon",
                "coordinates": [pixel_coords]
            },
            "properties": {
                "objectType": "annotation",
                "name": codes[code].lower().replace(" ", "-") + f"_{idx}",
                "isLocked": True
            }
        }

        features.append(feature)

    if features:
        geojson = {
            "type": "FeatureCollection",
            "features": features
        }
    else:
        geojson = None

    return geojson


if __name__ == "__main__":
    main()
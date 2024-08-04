import qupath.lib.roi.RectangleROI
import qupath.lib.roi.LineROI

// Get image data
def imageData = getCurrentImageData()
def server = imageData.getServer()
def cal = server.getPixelCalibration()

// Figure 2 - 1250 x 1250
// 2500, 30 for ACC3
// 6440,  4115 SqCC3

// Figure 6
// NB4
// 4050, 6780 - 550 x 550

// ACC25/NSG2a - 750 x 750
// 8400, 2700 



// Define starting pixel
def start_px_x = 4050 / cal.getPixelWidthMicrons()
def start_px_y = 6780 / cal.getPixelHeightMicrons()

// Define annotation x and y dimensions 
def size_px_x = 550 / cal.getPixelWidthMicrons()
def size_px_y = 550 / cal.getPixelHeightMicrons()

def line_start_x = start_px_x + (size_px_x / 12)
def line_start_y = (start_px_y + size_px_y) - (size_px_y / 12)

def length = 250 / cal.getPixelWidthMicrons()
def height = 50

def rois = []

// Create a new Rectangle ROI
rois.add(new RectangleROI(start_px_x, start_px_y, size_px_x, size_px_y))
//rois.add(new RectangleROI(line_start_x, line_start_y, length, height))


// Create & new annotation & add it to the object hierarchy

for (roi in rois) {
    addObjects(PathObjects.createAnnotationObject(roi))
}

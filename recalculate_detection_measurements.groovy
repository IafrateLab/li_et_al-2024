import groovyx.gpars.GParsPool
import qupath.lib.analysis.features.ObjectMeasurements

clearDetectionMeasurements()

println 'Detection measurements removed'

// Arguments that will be used for cell intensity and shape measurements
def server = getCurrentServer()
def cal = server.getPixelCalibration()
def downsample = 1.0

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

println 'Detection measurements recalculated'
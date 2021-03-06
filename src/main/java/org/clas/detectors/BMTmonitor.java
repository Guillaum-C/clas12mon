package org.clas.detectors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.clas.structures.Cluster;
import org.clas.structures.Event;
import org.clas.structures.Hit;
import org.clas.viewer.DetectorMonitor;
import org.clas.viewer.EventViewer;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;
import org.jlab.groot.group.DataGroup;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.services.CVTReconstruction;
import org.jlab.utils.groups.IndexedTable;

/**
 * @author guillaum
 */
public class BMTmonitor extends DetectorMonitor {
	
	int count=0;
	
	/* ===== GEOMETRY CONSTANTS ===== */
	
	int numberOfLayers;
	int numberOfSectors;
	int numberOfStrips[];
	int maxNumberOfStrips;
	int numberOfChips[];
	int maxNumberOfChips;
	int numberOfStripsPerChip = 64;
	int isZ[];
	
	/* ===== DAQ CONSTANTS ===== */
	
	int  runNumber = 10;
	boolean mask[][][];
	short adcOffset;
	double sigmaThreshold;
	double noise = 10; /* To be added in CCDB */
	int samplingTime;
	int numberOfSamples = 6; /* To be added in CCDB */
	boolean numberOfSamplesTested = false;
	int sparseReading = 1;
	
	/* ===== DATA STORAGE & DISPLAY ===== */
	
	int numberOfHitsPerStrip[][][];
	int numberOfCentroidsPerStrip[][][];
	int numberOfCentroidsMatchedPerStrip[][][];
	int numberOfHitsPerDream[];
	
	int ratePlotScale=10;
	
	MVTpulseViewer pulseViewer;
	
	/* ===== RECONSTRUCTION ===== */
	
	private CVTReconstruction recoCo;
	
	double efficiencyTrackLayer[];
	int efficiencyTrackLayerNb[];
	double efficiencyTrackTile[][];
	int efficiencyTrackTileNb[][];
	
	/**
	 * Main method
	 * @param name
	 */
	public BMTmonitor(String name, /* MVT PULSE DISPLAY */ MVTpulseViewer pulseViewer) {
		
		super(name);
		
		this.pulseViewer=pulseViewer;
		
		/* ===== LOAD GEOMETRY CONSTANTS ===== */
		
		DatabaseConstantProvider geometryConstants = new DatabaseConstantProvider(runNumber, "default");
		geometryConstants.loadTable("/geometry/cvt/mvt/bmt_layer");
		numberOfSectors = geometryConstants.getInteger("/geometry/cvt/mvt/bmt_layer/Nsector", 0);
		numberOfLayers = geometryConstants.length("/geometry/cvt/mvt/bmt_layer/Layer");
		
		numberOfStrips = new int[numberOfLayers + 1];
		numberOfChips = new int[numberOfLayers + 1];
		isZ = new int[numberOfLayers +1];
		
		for (int layer = 1; layer <= numberOfLayers; layer++) {
			numberOfStrips[layer] = geometryConstants.getInteger("/geometry/cvt/mvt/bmt_layer/Nstrip", (layer-1) );
			isZ[layer] = geometryConstants.getInteger("/geometry/cvt/mvt/bmt_layer/Axis", (layer-1) );
			if (numberOfStrips[layer]>maxNumberOfStrips){maxNumberOfStrips=numberOfStrips[layer];}
			numberOfChips[layer] = numberOfStrips[layer]/numberOfStripsPerChip;
			maxNumberOfChips+=numberOfSectors*numberOfChips[layer];
		}
		
		/* ===== LOAD DAQ CONSTANTS ===== */
		
		List<String> keysFitter   = Arrays.asList(new String[]{"BMT","FMT"});
		List<String> tablesFitter = Arrays.asList(new String[]{"/daq/config/bmt","/daq/config/fmt"});
		ConstantsManager  fitterManager      = new ConstantsManager();
		fitterManager.init(keysFitter, tablesFitter);
		IndexedTable daqConstants = fitterManager.getConstants(runNumber, keysFitter.get(0));
		samplingTime = (byte) daqConstants.getDoubleValue("sampling_time", 0, 0, 0);
		adcOffset = (short) daqConstants.getDoubleValue("adc_offset", 0, 0, 0);
		sigmaThreshold = (short) daqConstants.getDoubleValue("adc_threshold", 0, 0, 0);
       
		mask = new boolean[numberOfSectors + 1][numberOfLayers + 1][maxNumberOfStrips + 1];
		for (int sector = 1; sector <= numberOfSectors; sector++) {
			for (int layer = 1; layer <= numberOfLayers; layer++) {
				for (int component = 1 ; component <= numberOfStrips[layer]; component++){
					mask[sector][layer][component]=true;
				}
			}
		}
//		mask[1][1][80] = false;
//		mask[1][1][82] = false;
//		
//		mask[2][1][179] = false;
//		mask[2][1][192] = false;
//		mask[2][1][544] = false;
//		mask[2][1][545] = false;
//		
//		mask[3][4][757] = false;
//		
//		mask[3][5][102] = false; 
//		mask[3][5][735] = false;
		
		/* ===== INITIALIZE DATA STORAGE ===== */
		
		numberOfHitsPerStrip             = new int[numberOfSectors + 1][numberOfLayers + 1][maxNumberOfStrips+1];
		numberOfCentroidsPerStrip        = new int[numberOfSectors + 1][numberOfLayers + 1][maxNumberOfStrips+1];
		numberOfCentroidsMatchedPerStrip = new int[numberOfSectors + 1][numberOfLayers + 1][maxNumberOfStrips+1];
		numberOfHitsPerDream             = new int[maxNumberOfChips + 1];
		
		/* ===== RECONSTRUCTION ===== */
		
		recoCo = new CVTReconstruction();
		recoCo.init();
		
		efficiencyTrackLayer = new double[numberOfLayers + 1];
		efficiencyTrackTile = new double[numberOfSectors + 1][numberOfLayers + 1];
		for (int layer = 1; layer <= numberOfLayers; layer++) {
			efficiencyTrackLayer[layer]=0;
			for (int sector = 1; sector <= numberOfSectors; sector++) {
				efficiencyTrackTile[sector][layer]=0;
			}
		}
		
		efficiencyTrackLayerNb = new int[numberOfLayers + 1];
		efficiencyTrackTileNb = new int[numberOfSectors + 1][numberOfLayers + 1];
		for (int layer = 1; layer <= numberOfLayers; layer++) {
			efficiencyTrackLayerNb[layer]=0;
			for (int sector = 1; sector <= numberOfSectors; sector++) {
				efficiencyTrackTileNb[sector][layer]=0;
			}
		}
		
		/* ===== DECLARE TABS ===== */
		
		this.setDetectorTabNames("Occupancies", "Occupancy", "Occupancy C", "Occupancy Z", "NbHits vs Time", "Tile Multiplicity", "Tile Occupancy", "MaxADC", "MaxADC vs Strip", "IntegralPulse", "IntegralPulse vs Strip", "TimeMax", "TimeMaxCut","TimeMaxNoFit", "TimeMax vs Strip", "TimeMax per Dream", "ToT", "ToT per strip","FToT","FToT per strip", "OccupancyStrip", "OccupancyClusters", "NbClusters vs Time", "Cluster Multiplicity", "ClusterCharge", "ClusterCharge per strip", "ClusterSize", "ClusterSize per strip", "ClusterSize vs angle", "Occupancy vs angle", "OccupancyReco", "Residuals", "MaxAdcOfCentroid", "MaxAdcOfCentroid per strip", "TimeOfCentroid", "TimeOfCentroid per strip","hitMultiplicity");
		this.init(false);
	}

	/**
	 * Create histograms, define legends, fill colors
	 */
	@Override
	public void createHistos() {
		
		this.setNumberOfEvents(0);
		
		/* ===== CREATE OCCUPANCY HISTOS ===== */
		
		DataGroup occupancyGroup = new DataGroup("");
		this.getDataGroup().add(occupancyGroup, 0, 0, 0);
		
		H2F occupancyHisto = new H2F("Occupancies","Occupancies",maxNumberOfStrips, 0, maxNumberOfStrips, numberOfLayers*numberOfSectors,0,numberOfLayers*numberOfSectors);
		occupancyHisto.setTitleX("Strips");
		occupancyHisto.setTitleY("Detector");
		occupancyGroup.addDataSet(occupancyHisto, 0);
		
		H1F hitMultiplicityHisto = new H1F("hitMultiplicity", "hitMultiplicity", 1000, 1., 1001);
		hitMultiplicityHisto.setTitleX("Multiplicity");
		hitMultiplicityHisto.setTitleY("Nb events");
		occupancyGroup.addDataSet(hitMultiplicityHisto, 3);
		
		for (int sector = 1; sector <= numberOfSectors; sector++) {
			for (int layer = 1; layer <= numberOfLayers; layer++) {
				H1F hitmapHisto = new H1F("Hitmap : Layer " + layer + " Sector " + sector, "Hitmap : Layer " + layer + " Sector " + sector,
						(numberOfStrips[layer]), 1., (double) (numberOfStrips[layer])+1);
				hitmapHisto.setTitleX("Strips (Layer " + layer  + " Sector " + sector+")");
				hitmapHisto.setTitleY("Nb hits");
				if (isZ[layer]==1){
					hitmapHisto.setFillColor(4);
				}else{
					hitmapHisto.setFillColor(8);
				}
				occupancyGroup.addDataSet(hitmapHisto, 1);
				
				H1F hitsVSTimeHisto = new H1F("NbHits vs Time : Layer " + layer + " Sector " + sector, "NbHits vs Time : Layer " + layer + " Sector " + sector,
						100, 0, 100);
				hitsVSTimeHisto.setTitleX("Events (One bin is "+ratePlotScale+" events)");
				hitsVSTimeHisto.setTitleY("Nb hits");
				if (isZ[layer]==1){
					hitsVSTimeHisto.setFillColor(4);
				}else{
					hitsVSTimeHisto.setFillColor(8);
				}
				occupancyGroup.addDataSet(hitsVSTimeHisto, 2);
				
				H1F multiplicityHisto = new H1F("Multiplicity : Layer " + layer + " Sector " + sector, "Multiplicity :Layer " + layer + " Sector " + sector,
						300, 0, 300);
				multiplicityHisto.setTitleX("Multiplicity (Layer " + layer + " Sector " + sector+")");
				//multiplicityHisto.setTitleY("Nb hits");
				if (isZ[layer]==1){
					multiplicityHisto.setFillColor(4);
				}else{
					multiplicityHisto.setFillColor(8);
				}
				occupancyGroup.addDataSet(multiplicityHisto, 4);
				
				H1F tileOccupancyHisto = new H1F("TileOccupancy : Layer " + layer + " Sector " + sector, "TileOccupancy : Layer " + layer + " Sector " + sector,
						100, 0., 25);
				tileOccupancyHisto.setTitleX("Tile occupancy");
				tileOccupancyHisto.setTitleY("NumberOfEvents");
				if (isZ[layer]==1){
					tileOccupancyHisto.setFillColor(4);
				}else{
					tileOccupancyHisto.setFillColor(8);
				}
				occupancyGroup.addDataSet(tileOccupancyHisto, 5);
				
			}
		}
		
		/* ===== CREATE ADC HISTOS ===== */
		
		DataGroup adcGroup = new DataGroup("adc");
		this.getDataGroup().add(adcGroup, 0, 0, 1);
		
		for (int sector = 1; sector <= numberOfSectors; sector++) {
			for (int layer = 1; layer <= numberOfLayers; layer++) {				
				H1F adcMaxHisto = new H1F("ADCMax : Layer " + layer + " Sector " + sector, "ADCMax :Layer " + layer + " Sector " + sector,
						6000, -1000, 5000);
				adcMaxHisto.setTitleX("ADC max (Layer " + layer + " Sector " + sector+")");
				adcMaxHisto.setTitleY("Nb hits");
				if (isZ[layer]==1){
					adcMaxHisto.setFillColor(4);
				}else{
					adcMaxHisto.setFillColor(8);
				}
				adcGroup.addDataSet(adcMaxHisto, 0);
				
				H1F adcMaxVSStripHisto = new H1F("ADCMax vs Strip : Layer " + layer + " Sector " + sector, "ADCMax per strip : Layer " + layer + " Sector " + sector,
						numberOfStrips[layer], 1., (double) (numberOfStrips[layer])+1);
				adcMaxVSStripHisto.setTitleX("Strips (Layer " + layer + " Sector " + sector+")");
				adcMaxVSStripHisto.setTitleY("Integral of pulse per strip");
				if (isZ[layer]==1){
					adcMaxVSStripHisto.setFillColor(4);
				}else{
					adcMaxVSStripHisto.setFillColor(8);
				}
				adcGroup.addDataSet(adcMaxVSStripHisto, 1);
				
				H1F adcIntegralHisto = new H1F("IntegralPulse : Layer " + layer + " Sector " + sector, "IntegralPulse : Layer " + layer + " Sector " + sector,
						2000, 1., 20000);
				adcIntegralHisto.setTitleX("Integral of pulse (Layer " + layer + " Sector " + sector+")");
				adcIntegralHisto.setTitleY("Nb hits");
				if (isZ[layer]==1){
					adcIntegralHisto.setFillColor(4);
				}else{
					adcIntegralHisto.setFillColor(8);
				}
				adcGroup.addDataSet(adcIntegralHisto, 2);
				
				H1F adcIntegralVSStripHisto = new H1F("IntegralPulse vs Strip : Layer " + layer + " Sector " + sector, "IntegralPulse per strip : Layer " + layer + " Sector " + sector,
						(numberOfStrips[layer]), 1., (double) (numberOfStrips[layer])+1);
				adcIntegralVSStripHisto.setTitleX("Strips (Layer " + layer + " Sector " + sector+")");
				adcIntegralVSStripHisto.setTitleY("Integral of pulse per strip");
				if (isZ[layer]==1){
					adcIntegralVSStripHisto.setFillColor(4);
				}else{
					adcIntegralVSStripHisto.setFillColor(8);
				}
				adcGroup.addDataSet(adcIntegralVSStripHisto, 3);
			}
		}
		
		/* ===== CREATE TIME HISTOS ===== */
		
		DataGroup timeGroup = new DataGroup("time");
		this.getDataGroup().add(timeGroup, 0,0,2);
		
		H1F timeOfMaxHisto = new H1F("TimeOfMax","TimeOfMax",maxNumberOfChips,0,maxNumberOfChips);
		timeOfMaxHisto.setTitleX("Electronic chip");
		timeOfMaxHisto.setTitleY("Time of max adc");
		timeOfMaxHisto.setFillColor(4);
		timeGroup.addDataSet(timeOfMaxHisto, 0);
	
		for (int sector = 1; sector <= numberOfSectors; sector++) {
			for (int layer = 1; layer <= numberOfLayers; layer++) {
				H1F timeMaxHisto = new H1F("TimeOfMax : Layer " + layer + " Sector " + sector, "TimeOfMax : Layer " + layer + " Sector " + sector,
						samplingTime*(numberOfSamples*(1+sparseReading)-1)+1, 0,samplingTime*(numberOfSamples*(1+sparseReading)-1) );
				timeMaxHisto.setTitleX("Time of max (Layer " + layer + " Sector " + sector+")");
				timeMaxHisto.setTitleY("Nb hits");
				if (isZ[layer]==1){
					timeMaxHisto.setFillColor(4);
				}else{
					timeMaxHisto.setFillColor(8);
				}
				timeGroup.addDataSet(timeMaxHisto, 1);
				
				H1F timeMaxCutHisto = new H1F("TimeOfMax cut : Layer " + layer + " Sector " + sector, "TimeOfMax cut : Layer " + layer + " Sector " + sector,
						samplingTime*(numberOfSamples*(1+sparseReading)-1)+1, 0,samplingTime*(numberOfSamples*(1+sparseReading)-1) );
				timeMaxCutHisto.setTitleX("Time of max (Layer " + layer + " Sector " + sector+")");
				timeMaxCutHisto.setTitleY("Nb hits");
				if (isZ[layer]==1){
					timeMaxCutHisto.setFillColor(4);
				}else{
					timeMaxCutHisto.setFillColor(8);
				}
				timeGroup.addDataSet(timeMaxCutHisto, 2);
				
				H1F timeMaxNoFitHisto = new H1F("TimeOfMax no fit : Layer " + layer + " Sector " + sector, "TimeOfMax no fit : Layer " + layer + " Sector " + sector,
						samplingTime*(numberOfSamples*(1+sparseReading)-1)+1, 0,samplingTime*(numberOfSamples*(1+sparseReading)-1) );
				timeMaxNoFitHisto.setTitleX("Time of max (Layer " + layer + " Sector " + sector+")");
				timeMaxNoFitHisto.setTitleY("Nb hits");
				if (isZ[layer]==1){
					timeMaxNoFitHisto.setFillColor(4);
				}else{
					timeMaxNoFitHisto.setFillColor(8);
				}
				timeGroup.addDataSet(timeMaxNoFitHisto, 3);
				
				H1F timeMaxVSStripHisto = new H1F("TimeOfMax vs Strip : Layer " + layer + " Sector " + sector, "TimeOfMax vs Strip : Layer " + layer + " Sector " + sector,
						(numberOfStrips[layer]), 1., (double) (numberOfStrips[layer])+1);
				timeMaxVSStripHisto.setTitleX("Strips (Layer " + layer + " Sector " + sector+")");
				timeMaxVSStripHisto.setTitleY("Time of max per strip");
				if (isZ[layer]==1){
					timeMaxVSStripHisto.setFillColor(4);
				}else{
					timeMaxVSStripHisto.setFillColor(8);
				}
				timeGroup.addDataSet(timeMaxVSStripHisto, 4);
				
				H1F totHisto = new H1F("ToT : Layer " + layer + " Sector " + sector, "ToT : Layer " + layer + " Sector " + sector,
						numberOfSamples-1, 0.,numberOfSamples-1 );
				totHisto.setTitleX("ToT (Layer " + layer + " Sector " + sector+")");
				totHisto.setTitleY("Nb hits");
				if (isZ[layer]==1){
					totHisto.setFillColor(4);
				}else{
					totHisto.setFillColor(8);
				}
				timeGroup.addDataSet(totHisto, 5);
				
				H1F totPerStripHisto = new H1F("ToT per strip : Layer " + layer + " Sector " + sector, "ToT per strip : Layer " + layer + " Sector " + sector,
						(numberOfStrips[layer]), 1., (double) (numberOfStrips[layer])+1);
				totPerStripHisto.setTitleX("Strips (Layer " + layer + " Sector " + sector+")");
				totPerStripHisto.setTitleY("Time over threshold");
				if (isZ[layer]==1){
					totPerStripHisto.setFillColor(4);
				}else{
					totPerStripHisto.setFillColor(8);
				}
				timeGroup.addDataSet(totPerStripHisto, 6);				
				
				H1F ftotHisto = new H1F("FToT : Layer " + layer + " Sector " + sector, "FToT : Layer " + layer + " Sector " + sector,
						numberOfSamples-1, 0.,numberOfSamples-1 );
				ftotHisto.setTitleX("Ftot (Layer " + layer + " Sector " + sector+")");
				ftotHisto.setTitleY("Nb hits");
				if (isZ[layer]==1){
					ftotHisto.setFillColor(4);
				}else{
					ftotHisto.setFillColor(8);
				}
				timeGroup.addDataSet(ftotHisto, 7);
				
				H1F ftotPerStripHisto = new H1F("FToT per strip : Layer " + layer + " Sector " + sector, "FToT per strip : Layer " + layer + " Sector " + sector,
						(numberOfStrips[layer]), 1., (double) (numberOfStrips[layer])+1);
				ftotPerStripHisto.setTitleX("Strips (Layer " + layer + " Sector " + sector+")");
				ftotPerStripHisto.setTitleY("First time over threshold");
				if (isZ[layer]==1){
					ftotPerStripHisto.setFillColor(4);
				}else{
					ftotPerStripHisto.setFillColor(8);
				}
				timeGroup.addDataSet(ftotPerStripHisto, 8);
			}
		}

		/* ===== CREATE RECO HISTOS ===== */

		DataGroup effGroup = new DataGroup("efficiency");
		this.getDataGroup().add(effGroup, 0, 0, 3);

		for (int sector = 1; sector <= numberOfSectors; sector++) {
			for (int layer = 1; layer <= numberOfLayers; layer++) {
				H1F residualsHisto = new H1F("Residuals : Layer " + layer + " Sector " + sector,
						"Residuals : Layer " + layer + " Sector " + sector, 100, -100.,100.);
				residualsHisto.setTitleX("Residuals (Layer " + layer + " Sector " + sector + ")");
				residualsHisto.setTitleY("Nb hits");
				if (isZ[layer]==1) {
					residualsHisto.setFillColor(4);
				} else {
					residualsHisto.setFillColor(8);
				}
				effGroup.addDataSet(residualsHisto, 0);

//				H1F residualCenterVSStripHisto = new H1F("Residuals center vs Strip : Layer " + layer + " Sector " + sector,
//						"Residuals center vs Strip : Layer " + layer + " Sector " + sector, (numberStrips[layer]), 1.,
//						(double) (numberStrips[layer]) + 1);
//				residualCenterVSStripHisto.setTitleX("Strips (Layer " + layer + " Sector " + sector + ")");
//				residualCenterVSStripHisto.setTitleY("Residuals center");
//				if (isZ[layer]==1) {
//					residualCenterVSStripHisto.setFillColor(4);
//				} else {
//					residualCenterVSStripHisto.setFillColor(8);
//				}
//				effGroup.addDataSet(residualCenterVSStripHisto, 1);
				
//				H1F residualWidthVSStripHisto = new H1F("Residuals width vs Strip : Layer " + layer + " Sector " + sector,
//						"Residuals width vs Strip : Layer " + layer + " Sector " + sector, (numberStrips[layer]), 1.,
//						(double) (numberStrips[layer]) + 1);
//				residualWidthVSStripHisto.setTitleX("Strips (Layer " + layer + " Sector " + sector + ")");
//				residualWidthVSStripHisto.setTitleY("Residuals width");
//				if (isZ[layer]==1) {
//					residualWidthVSStripHisto.setFillColor(4);
//				} else {
//					residualWidthVSStripHisto.setFillColor(8);
//				}
//				effGroup.addDataSet(residualWidthVSStripHisto, 2);
			}
		}
		
		for (int sector = 1; sector <= numberOfSectors; sector++) {
			for (int layer = 1; layer <= numberOfLayers; layer++) {
				
				H1F hitmapHisto = new H1F("HitmapClusters : Layer " + layer + " Sector " + sector, "HitmapClusters : Layer " + layer + " Sector " + sector,
						(numberOfStrips[layer]), 1., (double) (numberOfStrips[layer])+1);
				hitmapHisto.setTitleX("Strips (Layer " + layer  + " Sector " + sector+")");
				hitmapHisto.setTitleY("Nb clusters");
				if (isZ[layer]==1){
					hitmapHisto.setFillColor(4);
				}else{
					hitmapHisto.setFillColor(8);
				}
				occupancyGroup.addDataSet(hitmapHisto, 3);
				
				H1F hitsVSTimeHisto = new H1F("NbClusters vs Time : Layer " + layer + " Sector " + sector, "NbClusters vs Time : Layer " + layer + " Sector " + sector,
						100, 0, 100);
				hitsVSTimeHisto.setTitleX("Time (Layer " + layer  + " Sector " + sector+")");
				hitsVSTimeHisto.setTitleY("Nb clusters");
				if (isZ[layer]==1){
					hitsVSTimeHisto.setFillColor(4);
				}else{
					hitsVSTimeHisto.setFillColor(8);
				}
				occupancyGroup.addDataSet(hitsVSTimeHisto, 4);
				
				H1F stripMultiplicityHisto = new H1F("OccupancyStrip : Layer " + layer + " Sector " + sector, "HitmapClusters : Layer " + layer + " Sector " + sector,
						(numberOfStrips[layer]), 1., (double) (numberOfStrips[layer])+1);
				stripMultiplicityHisto.setTitleX("Strips (Layer " + layer  + " Sector " + sector+")");
				stripMultiplicityHisto.setTitleY("Occupancy (%)");
				if (isZ[layer]==1){
					stripMultiplicityHisto.setFillColor(4);
				}else{
					stripMultiplicityHisto.setFillColor(8);
				}
				occupancyGroup.addDataSet(stripMultiplicityHisto, 8);
				
				H1F clusterMultiplicityHisto = new H1F("Cluster Multiplicity : Layer " + layer + " Sector " + sector, "Cluster Multiplicity :Layer " + layer + " Sector " + sector,
						51, -0.5, 50.5);
				clusterMultiplicityHisto.setTitleX("Cluster Multiplicity (Layer " + layer + " Sector " + sector+")");
				clusterMultiplicityHisto.setTitleY("Nb events");
				if (isZ[layer]==1){
					clusterMultiplicityHisto.setFillColor(4);
				}else{
					clusterMultiplicityHisto.setFillColor(8);
				}
				clusterMultiplicityHisto.setOptStat(110);
				occupancyGroup.addDataSet(clusterMultiplicityHisto, 5);
				
				
				H1F clusterChargeHisto = new H1F("ClusterCharge : Layer " + layer + " Sector " + sector,
						"ClusterCharge : Layer " + layer + " Sector " + sector, 6000, -1000,5000.);
				clusterChargeHisto.setTitleX("ClusterCharge (Layer " + layer + " Sector " + sector + ")");
				clusterChargeHisto.setTitleY("Nb hits");
				if (isZ[layer]==1) {
					clusterChargeHisto.setFillColor(4);
				} else {
					clusterChargeHisto.setFillColor(8);
				}
				adcGroup.addDataSet(clusterChargeHisto, 4);
				
				H1F clusterChargePerStripHisto = new H1F("ClusterCharge per strip : Layer " + layer + " Sector " + sector,
						"ClusterCharge per strip : Layer " + layer + " Sector " + sector, (numberOfStrips[layer]), 1., (double) (numberOfStrips[layer])+1);
				clusterChargePerStripHisto.setTitleX("Strips (Layer " + layer + " Sector " + sector+")");
				clusterChargePerStripHisto.setTitleY("Cluster Charge");
				if (isZ[layer]==1) {
					clusterChargePerStripHisto.setFillColor(4);
				} else {
					clusterChargePerStripHisto.setFillColor(8);
				}
				adcGroup.addDataSet(clusterChargePerStripHisto, 5);
				
				H1F clusterSizeHisto = new H1F("ClusterSize : Layer " + layer + " Sector " + sector,
						"ClusterSize : Layer " + layer + " Sector " + sector, 100, 0,100.);
				clusterSizeHisto.setTitleX("ClusterSize (Layer " + layer + " Sector " + sector + ")");
				clusterSizeHisto.setTitleY("Nb hits");
				if (isZ[layer]==1) {
					clusterSizeHisto.setFillColor(4);
				} else {
					clusterSizeHisto.setFillColor(8);
				}
				adcGroup.addDataSet(clusterSizeHisto, 6);
				
				H1F clusterSizePerStripHisto = new H1F("ClusterSize per strip : Layer " + layer + " Sector " + sector,
						"ClusterSize per strip : Layer " + layer + " Sector " + sector, (numberOfStrips[layer]), 1., (double) (numberOfStrips[layer])+1);
				clusterSizePerStripHisto.setTitleX("Strips (Layer " + layer + " Sector " + sector+")");
				clusterSizePerStripHisto.setTitleY("Cluster Size");
				if (isZ[layer]==1) {
					clusterSizePerStripHisto.setFillColor(4);
				} else {
					clusterSizePerStripHisto.setFillColor(8);
				}
				adcGroup.addDataSet(clusterSizePerStripHisto, 7);
				
				H1F clusterSizeVsAngleHisto = new H1F("ClusterSize vs angle : Layer " + layer + " Sector " + sector,
						"ClusterSize vs angle : Layer " + layer + " Sector " + sector, 360, -180, 180);
				clusterSizeVsAngleHisto.setTitleX("Angle (Layer " + layer + " Sector " + sector+")");
				clusterSizeVsAngleHisto.setTitleY("Cluster Size");
				if (isZ[layer]==1) {
					clusterSizeVsAngleHisto.setFillColor(4);
				} else {
					clusterSizeVsAngleHisto.setFillColor(8);
				}
				adcGroup.addDataSet(clusterSizeVsAngleHisto, 11);
				
				H1F occupancyVsAngleHisto = new H1F("Occupancy vs angle : Layer " + layer + " Sector " + sector,
						"Occupancy vs angle : Layer " + layer + " Sector " + sector, 360, -180, 180);
				occupancyVsAngleHisto.setTitleX("Angle (Layer " + layer + " Sector " + sector+")");
				occupancyVsAngleHisto.setTitleY("Number of tracks");
				if (isZ[layer]==1) {
					occupancyVsAngleHisto.setFillColor(4);
				} else {
					occupancyVsAngleHisto.setFillColor(8);
				}
				adcGroup.addDataSet(occupancyVsAngleHisto, 8);
				
				H1F maxAdcCentroidHisto = new H1F("MaxAdcOfCentroid : Layer " + layer + " Sector " + sector,
						"MaxAdcOfCentroid : Layer " + layer + " Sector " + sector, 600, -1000, 5000.);
				maxAdcCentroidHisto.setTitleX("MaxAdcOfCentroid (Layer " + layer + " Sector " + sector + ")");
				maxAdcCentroidHisto.setTitleY("Nb hits");
				if (isZ[layer]==1) {
					maxAdcCentroidHisto.setFillColor(4);
				} else {
					maxAdcCentroidHisto.setFillColor(8);
				}
				adcGroup.addDataSet(maxAdcCentroidHisto, 9);
				
				H1F maxAdcCentroidHistoPerStripHisto = new H1F("MaxAdcOfCentroid per strip : Layer " + layer + " Sector " + sector,
						"MaxAdcOfCentroid per strip : Layer " + layer + " Sector " + sector, (numberOfStrips[layer]), 1., (double) (numberOfStrips[layer])+1);
				maxAdcCentroidHistoPerStripHisto.setTitleX("Strips (Layer " + layer + " Sector " + sector+")");
				maxAdcCentroidHistoPerStripHisto.setTitleY("Adc");
				if (isZ[layer]==1) {
					maxAdcCentroidHistoPerStripHisto.setFillColor(4);
				} else {
					maxAdcCentroidHistoPerStripHisto.setFillColor(8);
				}
				adcGroup.addDataSet(maxAdcCentroidHistoPerStripHisto, 10);
				
				H1F timeCentroidHisto = new H1F("TimeOfCentroid : Layer " + layer + " Sector " + sector,
						"TimeOfCentroid : Layer " + layer + " Sector " + sector, samplingTime*(numberOfSamples+1), 1.,samplingTime*(numberOfSamples+1));
				timeCentroidHisto.setTitleX("TimeOfCentroid (Layer " + layer + " Sector " + sector + ")");
				timeCentroidHisto.setTitleY("Nb hits");
				if (isZ[layer]==1) {
					timeCentroidHisto.setFillColor(4);
				} else {
					timeCentroidHisto.setFillColor(8);
				}
				timeGroup.addDataSet(timeCentroidHisto, 7);
				
				H1F timeCentroidHistoPerStripHisto = new H1F("TimeOfCentroid per strip : Layer " + layer + " Sector " + sector,
						"TimeOfCentroid per strip : Layer " + layer + " Sector " + sector, (numberOfStrips[layer]), 1., (double) (numberOfStrips[layer])+1);
				timeCentroidHistoPerStripHisto.setTitleX("Strips (Layer " + layer + " Sector " + sector+")");
				timeCentroidHistoPerStripHisto.setTitleY("TimeOfMax");
				if (isZ[layer]==1) {
					timeCentroidHistoPerStripHisto.setFillColor(4);
				} else {
					timeCentroidHistoPerStripHisto.setFillColor(8);
				}
				timeGroup.addDataSet(timeCentroidHistoPerStripHisto, 8);
			
				H1F occupancyReco = new H1F("OccupancyReco : Layer " + layer + " Sector " + sector, "OccupancyReco : Layer " + layer + " Sector " + sector,
						(numberOfStrips[layer]), 1., (double) (numberOfStrips[layer])+1);
				occupancyReco.setTitleX("Strips (Layer " + layer  + " Sector " + sector+")");
				occupancyReco.setTitleY("Nb tracks");
				if (isZ[layer]==1){
					occupancyReco.setFillColor(4);
				}else{
					occupancyReco.setFillColor(8);
				}
				occupancyGroup.addDataSet(occupancyReco, 7);
				
			}
		}
		
		//pulseHistoBMT = new H1F("Pulse","Pulse", numberOfSamples, 1., numberOfSamples+1);
		
	}
	
	/**
	 * Divide canvas and draw histograms
	 */
	@Override
	public void plotHistos() {
		
		// initialize canvas and plot histograms
		this.getDetectorCanvas().getCanvas("Occupancies").setGridX(false);
		this.getDetectorCanvas().getCanvas("Occupancies").setGridY(false);
		this.getDetectorCanvas().getCanvas("Occupancies").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("Occupancies").setAxisLabelSize(12);
		this.getDetectorCanvas().getCanvas("Occupancies").draw(this.getDataGroup().getItem(0, 0, 0).getH2F("Occupancies"));
		this.getDetectorCanvas().getCanvas("Occupancies").update();
		
		this.getDetectorCanvas().getCanvas("Occupancy").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("Occupancy").setGridX(false);
		this.getDetectorCanvas().getCanvas("Occupancy").setGridY(false);
		this.getDetectorCanvas().getCanvas("Occupancy").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("Occupancy").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("Occupancy C").divide(numberOfSectors, numberOfLayers/2);
		this.getDetectorCanvas().getCanvas("Occupancy C").setGridX(false);
		this.getDetectorCanvas().getCanvas("Occupancy C").setGridY(false);
		this.getDetectorCanvas().getCanvas("Occupancy C").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("Occupancy C").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("Occupancy Z").divide(numberOfSectors, numberOfLayers/2);
		this.getDetectorCanvas().getCanvas("Occupancy Z").setGridX(false);
		this.getDetectorCanvas().getCanvas("Occupancy Z").setGridY(false);
		this.getDetectorCanvas().getCanvas("Occupancy Z").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("Occupancy Z").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("Occupancy Z").divide(numberOfSectors, numberOfLayers/2);
		this.getDetectorCanvas().getCanvas("Occupancy Z").setGridX(false);
		this.getDetectorCanvas().getCanvas("Occupancy Z").setGridY(false);
		this.getDetectorCanvas().getCanvas("Occupancy Z").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("Occupancy Z").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("NbHits vs Time").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("NbHits vs Time").setGridX(false);
		this.getDetectorCanvas().getCanvas("NbHits vs Time").setGridY(false);
		this.getDetectorCanvas().getCanvas("NbHits vs Time").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("NbHits vs Time").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("hitMultiplicity").setGridX(false);
		this.getDetectorCanvas().getCanvas("hitMultiplicity").setGridY(false);
		this.getDetectorCanvas().getCanvas("hitMultiplicity").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("hitMultiplicity").setAxisLabelSize(12);
		this.getDetectorCanvas().getCanvas("hitMultiplicity").draw(this.getDataGroup().getItem(0, 0, 0).getH1F("hitMultiplicity"));
		this.getDetectorCanvas().getCanvas("hitMultiplicity").update();
		
		this.getDetectorCanvas().getCanvas("Tile Multiplicity").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("Tile Multiplicity").setGridX(false);
		this.getDetectorCanvas().getCanvas("Tile Multiplicity").setGridY(false);
		this.getDetectorCanvas().getCanvas("Tile Multiplicity").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("Tile Multiplicity").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("Tile Occupancy").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("Tile Occupancy").setGridX(false);
		this.getDetectorCanvas().getCanvas("Tile Occupancy").setGridY(false);
		this.getDetectorCanvas().getCanvas("Tile Occupancy").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("Tile Occupancy").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("MaxADC").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("MaxADC").setGridX(false);
		this.getDetectorCanvas().getCanvas("MaxADC").setGridY(false);
		this.getDetectorCanvas().getCanvas("MaxADC").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("MaxADC").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("MaxADC vs Strip").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("MaxADC vs Strip").setGridX(false);
		this.getDetectorCanvas().getCanvas("MaxADC vs Strip").setGridY(false);
		this.getDetectorCanvas().getCanvas("MaxADC vs Strip").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("MaxADC vs Strip").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("IntegralPulse").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("IntegralPulse").setGridX(false);
		this.getDetectorCanvas().getCanvas("IntegralPulse").setGridY(false);
		this.getDetectorCanvas().getCanvas("IntegralPulse").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("IntegralPulse").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("IntegralPulse vs Strip").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("IntegralPulse vs Strip").setGridX(false);
		this.getDetectorCanvas().getCanvas("IntegralPulse vs Strip").setGridY(false);
		this.getDetectorCanvas().getCanvas("IntegralPulse vs Strip").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("IntegralPulse vs Strip").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("TimeMax").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("TimeMax").setGridX(false);
		this.getDetectorCanvas().getCanvas("TimeMax").setGridY(false);
		this.getDetectorCanvas().getCanvas("TimeMax").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("TimeMax").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("TimeMaxCut").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("TimeMaxCut").setGridX(false);
		this.getDetectorCanvas().getCanvas("TimeMaxCut").setGridY(false);
		this.getDetectorCanvas().getCanvas("TimeMaxCut").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("TimeMaxCut").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("TimeMaxNoFit").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("TimeMaxNoFit").setGridX(false);
		this.getDetectorCanvas().getCanvas("TimeMaxNoFit").setGridY(false);
		this.getDetectorCanvas().getCanvas("TimeMaxNoFit").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("TimeMaxNoFit").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("TimeMax vs Strip").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("TimeMax vs Strip").setGridX(false);
		this.getDetectorCanvas().getCanvas("TimeMax vs Strip").setGridY(false);
		this.getDetectorCanvas().getCanvas("TimeMax vs Strip").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("TimeMax vs Strip").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("TimeMax per Dream").setGridX(false);
		this.getDetectorCanvas().getCanvas("TimeMax per Dream").setGridY(false);
		this.getDetectorCanvas().getCanvas("TimeMax per Dream").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("TimeMax per Dream").setAxisLabelSize(12);
		this.getDetectorCanvas().getCanvas("TimeMax per Dream").draw(this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfMax"));
		this.getDetectorCanvas().getCanvas("TimeMax per Dream").update();
		
		this.getDetectorCanvas().getCanvas("ToT").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("ToT").setGridX(false);
		this.getDetectorCanvas().getCanvas("ToT").setGridY(false);
		this.getDetectorCanvas().getCanvas("ToT").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("ToT").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("ToT per strip").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("ToT per strip").setGridX(false);
		this.getDetectorCanvas().getCanvas("ToT per strip").setGridY(false);
		this.getDetectorCanvas().getCanvas("ToT per strip").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("ToT per strip").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("FToT").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("FToT").setGridX(false);
		this.getDetectorCanvas().getCanvas("FToT").setGridY(false);
		this.getDetectorCanvas().getCanvas("FToT").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("FToT").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("FToT per strip").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("FToT per strip").setGridX(false);
		this.getDetectorCanvas().getCanvas("FToT per strip").setGridY(false);
		this.getDetectorCanvas().getCanvas("FToT per strip").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("FToT per strip").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("OccupancyStrip").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("OccupancyStrip").setGridX(false);
		this.getDetectorCanvas().getCanvas("OccupancyStrip").setGridY(false);
		this.getDetectorCanvas().getCanvas("OccupancyStrip").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("OccupancyStrip").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("OccupancyClusters").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("OccupancyClusters").setGridX(false);
		this.getDetectorCanvas().getCanvas("OccupancyClusters").setGridY(false);
		this.getDetectorCanvas().getCanvas("OccupancyClusters").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("OccupancyClusters").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("NbClusters vs Time").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("NbClusters vs Time").setGridX(false);
		this.getDetectorCanvas().getCanvas("NbClusters vs Time").setGridY(false);
		this.getDetectorCanvas().getCanvas("NbClusters vs Time").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("NbClusters vs Time").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("Cluster Multiplicity").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("Cluster Multiplicity").setGridX(false);
		this.getDetectorCanvas().getCanvas("Cluster Multiplicity").setGridY(false);
		this.getDetectorCanvas().getCanvas("Cluster Multiplicity").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("Cluster Multiplicity").setAxisLabelSize(12);
		for (int sector=1; sector<= numberOfSectors; sector++){
			for (int layer=1; layer<= numberOfLayers; layer++){
				this.getDetectorCanvas().getCanvas("Cluster Multiplicity").getPad(sector-1+(layer-1)*numberOfSectors).getAxisY().setLog(true);
			}
		}
		
		this.getDetectorCanvas().getCanvas("ClusterCharge").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("ClusterCharge").setGridX(false);
		this.getDetectorCanvas().getCanvas("ClusterCharge").setGridY(false);
		this.getDetectorCanvas().getCanvas("ClusterCharge").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("ClusterCharge").setAxisLabelSize(12);

		this.getDetectorCanvas().getCanvas("ClusterCharge per strip").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("ClusterCharge per strip").setGridX(false);
		this.getDetectorCanvas().getCanvas("ClusterCharge per strip").setGridY(false);
		this.getDetectorCanvas().getCanvas("ClusterCharge per strip").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("ClusterCharge per strip").setAxisLabelSize(12);

		this.getDetectorCanvas().getCanvas("ClusterSize").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("ClusterSize").setGridX(false);
		this.getDetectorCanvas().getCanvas("ClusterSize").setGridY(false);
		this.getDetectorCanvas().getCanvas("ClusterSize").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("ClusterSize").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("ClusterSize per strip").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("ClusterSize per strip").setGridX(false);
		this.getDetectorCanvas().getCanvas("ClusterSize per strip").setGridY(false);
		this.getDetectorCanvas().getCanvas("ClusterSize per strip").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("ClusterSize per strip").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("ClusterSize vs angle").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("ClusterSize vs angle").setGridX(false);
		this.getDetectorCanvas().getCanvas("ClusterSize vs angle").setGridY(false);
		this.getDetectorCanvas().getCanvas("ClusterSize vs angle").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("ClusterSize vs angle").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("Occupancy vs angle").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("Occupancy vs angle").setGridX(false);
		this.getDetectorCanvas().getCanvas("Occupancy vs angle").setGridY(false);
		this.getDetectorCanvas().getCanvas("Occupancy vs angle").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("Occupancy vs angle").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("OccupancyReco").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("OccupancyReco").setGridX(false);
		this.getDetectorCanvas().getCanvas("OccupancyReco").setGridY(false);
		this.getDetectorCanvas().getCanvas("OccupancyReco").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("OccupancyReco").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("Residuals").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("Residuals").setGridX(false);
		this.getDetectorCanvas().getCanvas("Residuals").setGridY(false);
		this.getDetectorCanvas().getCanvas("Residuals").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("Residuals").setAxisLabelSize(12);
		
		this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid").setGridX(false);
		this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid").setGridY(false);
		this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid").setAxisLabelSize(12);

		this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid per strip").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid per strip").setGridX(false);
		this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid per strip").setGridY(false);
		this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid per strip").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid per strip").setAxisLabelSize(12);

		this.getDetectorCanvas().getCanvas("TimeOfCentroid").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("TimeOfCentroid").setGridX(false);
		this.getDetectorCanvas().getCanvas("TimeOfCentroid").setGridY(false);
		this.getDetectorCanvas().getCanvas("TimeOfCentroid").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("TimeOfCentroid").setAxisLabelSize(12);

		this.getDetectorCanvas().getCanvas("TimeOfCentroid per strip").divide(numberOfSectors, numberOfLayers);
		this.getDetectorCanvas().getCanvas("TimeOfCentroid per strip").setGridX(false);
		this.getDetectorCanvas().getCanvas("TimeOfCentroid per strip").setGridY(false);
		this.getDetectorCanvas().getCanvas("TimeOfCentroid per strip").setAxisTitleSize(12);
		this.getDetectorCanvas().getCanvas("TimeOfCentroid per strip").setAxisLabelSize(12);
		
		for (int sector = 1; sector <= numberOfSectors; sector++) {
			for (int layer = 1; layer <= numberOfLayers; layer++) {
				int column=numberOfSectors - sector;
				int row;
				int numberOfColumns=numberOfSectors;
				
				switch (layer) {
				case 1: row=2; break;
				case 2: row=2; break;
				case 3: row=1; break;
				case 4: row=1; break;
				case 5: row=0; break;
				case 6: row=0; break;
				default:row=-1;break;
				}
				if (isZ[layer]==1){
					this.getDetectorCanvas().getCanvas("Occupancy Z").cd(column + numberOfColumns * row);
					this.getDetectorCanvas().getCanvas("Occupancy Z").draw(
						this.getDataGroup().getItem(0, 0, 0).getH1F("Hitmap : Layer " + layer + " Sector " + sector));
				}else{
					this.getDetectorCanvas().getCanvas("Occupancy C").cd(column + numberOfColumns * row);
					this.getDetectorCanvas().getCanvas("Occupancy C").draw(
						this.getDataGroup().getItem(0, 0, 0).getH1F("Hitmap : Layer " + layer + " Sector " + sector));
				}
				
				switch (layer) {
				case 1: row=5; break;
				case 2: row=2; break;
				case 3: row=1; break;
				case 4: row=4; break;
				case 5: row=0; break;
				case 6: row=3; break;
				default:row=-1;break;
				}
//				this.getDetectorCanvas().getCanvas("Occupancies").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("Occupancies").draw(
//						this.getDataGroup().getItem(sector, layer, 0).getH1F("Hitmap : Layer " + layer + " Sector " + sector));
//				
				this.getDetectorCanvas().getCanvas("Occupancy").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("Occupancy").draw(
						this.getDataGroup().getItem(0, 0, 0).getH1F("Hitmap : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("NbHits vs Time").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("NbHits vs Time").draw(
						this.getDataGroup().getItem(0, 0, 0).getH1F("NbHits vs Time : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("Tile Multiplicity").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("Tile Multiplicity").draw(
						this.getDataGroup().getItem(0, 0, 0).getH1F("Multiplicity : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("Tile Occupancy").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("Tile Occupancy").draw(
						this.getDataGroup().getItem(0, 0, 0).getH1F("TileOccupancy : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("MaxADC").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("MaxADC").draw(
						this.getDataGroup().getItem(0, 0, 1).getH1F("ADCMax : Layer " + layer + " Sector " + sector));

				this.getDetectorCanvas().getCanvas("MaxADC vs Strip").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("MaxADC vs Strip").draw(
						this.getDataGroup().getItem(0, 0, 1).getH1F("ADCMax vs Strip : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("IntegralPulse").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("IntegralPulse").draw(
						this.getDataGroup().getItem(0, 0, 1).getH1F("IntegralPulse : Layer " + layer + " Sector " + sector));

				this.getDetectorCanvas().getCanvas("IntegralPulse vs Strip").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("IntegralPulse vs Strip").draw(
						this.getDataGroup().getItem(0, 0, 1).getH1F("IntegralPulse vs Strip : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("TimeMax").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("TimeMax").draw(
						this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfMax : Layer " + layer + " Sector " + sector));

				this.getDetectorCanvas().getCanvas("TimeMaxCut").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("TimeMaxCut").draw(
						this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfMax cut : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("TimeMaxNoFit").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("TimeMaxNoFit").draw(
						this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfMax no fit : Layer " + layer + " Sector " + sector));

				this.getDetectorCanvas().getCanvas("TimeMax vs Strip").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("TimeMax vs Strip").draw(
						this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfMax vs Strip : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("ToT").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("ToT").draw(
						this.getDataGroup().getItem(0, 0, 2).getH1F("ToT : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("ToT per strip").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("ToT per strip").draw(
						this.getDataGroup().getItem(0, 0, 2).getH1F("ToT per strip : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("FToT").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("FToT").draw(
						this.getDataGroup().getItem(0, 0, 2).getH1F("FToT : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("FToT per strip").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("FToT per strip").draw(
						this.getDataGroup().getItem(0, 0, 2).getH1F("FToT per strip : Layer " + layer + " Sector " + sector));

				this.getDetectorCanvas().getCanvas("OccupancyStrip").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("OccupancyStrip").draw(
						this.getDataGroup().getItem(0, 0, 0).getH1F("OccupancyStrip : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("OccupancyClusters").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("OccupancyClusters").draw(
						this.getDataGroup().getItem(0, 0, 0).getH1F("HitmapClusters : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("NbClusters vs Time").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("NbClusters vs Time").draw(
						this.getDataGroup().getItem(0, 0, 0).getH1F("NbClusters vs Time : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("Cluster Multiplicity").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("Cluster Multiplicity").draw(
						this.getDataGroup().getItem(0, 0, 0).getH1F("Cluster Multiplicity : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("ClusterCharge").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("ClusterCharge").draw(
						this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterCharge : Layer " + layer + " Sector " + sector));

				this.getDetectorCanvas().getCanvas("ClusterCharge per strip").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("ClusterCharge per strip").draw(
						this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterCharge per strip : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("ClusterSize").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("ClusterSize").draw(
						this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterSize : Layer " + layer + " Sector " + sector));

				this.getDetectorCanvas().getCanvas("ClusterSize per strip").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("ClusterSize per strip").draw(
						this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterSize per strip : Layer " + layer + " Sector " + sector));

				this.getDetectorCanvas().getCanvas("Occupancy vs angle").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("Occupancy vs angle").draw(
						this.getDataGroup().getItem(0, 0, 1).getH1F("Occupancy vs angle : Layer " + layer + " Sector " + sector));

				this.getDetectorCanvas().getCanvas("ClusterSize vs angle").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("ClusterSize vs angle").draw(
						this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterSize vs angle : Layer " + layer + " Sector " + sector));

				this.getDetectorCanvas().getCanvas("OccupancyReco").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("OccupancyReco").draw(
						this.getDataGroup().getItem(0, 0, 0).getH1F("OccupancyReco : Layer " + layer + " Sector " + sector));

				this.getDetectorCanvas().getCanvas("Residuals").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("Residuals").draw(
						this.getDataGroup().getItem(0, 0, 3).getH1F("Residuals : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid").draw(
						this.getDataGroup().getItem(0, 0, 1).getH1F("MaxAdcOfCentroid : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid per strip").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid per strip").draw(
						this.getDataGroup().getItem(0, 0, 1).getH1F("MaxAdcOfCentroid per strip : Layer " + layer + " Sector " + sector));

				this.getDetectorCanvas().getCanvas("TimeOfCentroid").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("TimeOfCentroid").draw(
						this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfCentroid : Layer " + layer + " Sector " + sector));
				
				this.getDetectorCanvas().getCanvas("TimeOfCentroid per strip").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("TimeOfCentroid per strip").draw(
						this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfCentroid per strip : Layer " + layer + " Sector " + sector));
				
			}
			this.getDetectorCanvas().getCanvas("Occupancies").update();
			this.getDetectorCanvas().getCanvas("Occupancy").update();
			this.getDetectorCanvas().getCanvas("Occupancy C").update();
			this.getDetectorCanvas().getCanvas("Occupancy Z").update();
			this.getDetectorCanvas().getCanvas("NbHits vs Time").update();
			this.getDetectorCanvas().getCanvas("Tile Multiplicity").update();
			this.getDetectorCanvas().getCanvas("Tile Occupancy").update();

			this.getDetectorCanvas().getCanvas("MaxADC").update();
			this.getDetectorCanvas().getCanvas("MaxADC vs Strip").update();
			this.getDetectorCanvas().getCanvas("IntegralPulse").update();
			this.getDetectorCanvas().getCanvas("IntegralPulse vs Strip").update();
			
			this.getDetectorCanvas().getCanvas("TimeMax").update();
			this.getDetectorCanvas().getCanvas("TimeMaxCut").update();
			this.getDetectorCanvas().getCanvas("TimeMaxNoFit").update();
			this.getDetectorCanvas().getCanvas("TimeMax vs Strip").update();
			this.getDetectorCanvas().getCanvas("ToT").update();
			this.getDetectorCanvas().getCanvas("ToT per strip").update();
			this.getDetectorCanvas().getCanvas("FToT").update();
			this.getDetectorCanvas().getCanvas("FToT per strip").update();
			
			this.getDetectorCanvas().getCanvas("OccupancyStrip").update();
			this.getDetectorCanvas().getCanvas("OccupancyClusters").update();
			this.getDetectorCanvas().getCanvas("NbClusters vs Time").update();
			this.getDetectorCanvas().getCanvas("Cluster Multiplicity").update();
			this.getDetectorCanvas().getCanvas("ClusterCharge").update();
			this.getDetectorCanvas().getCanvas("ClusterCharge per strip").update();
			this.getDetectorCanvas().getCanvas("ClusterSize").update();
			this.getDetectorCanvas().getCanvas("ClusterSize per strip").update();
			this.getDetectorCanvas().getCanvas("ClusterSize vs angle").update();
			this.getDetectorCanvas().getCanvas("Occupancy vs angle").update();
			this.getDetectorCanvas().getCanvas("OccupancyReco").update();
			this.getDetectorCanvas().getCanvas("Residuals").update();
			this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid").update();
			this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid per strip").update();
			this.getDetectorCanvas().getCanvas("TimeOfCentroid").update();
			this.getDetectorCanvas().getCanvas("TimeOfCentroid per strip").update();
		}
	}
	
	public void updateHistoChanges() {
		for (int sector = 1; sector <= numberOfSectors; sector++) {
			for (int layer = 1; layer <= numberOfLayers; layer++) {
				int column=numberOfSectors - sector;
				int row;
				int numberOfColumns=numberOfSectors;
				
				switch (layer) {
				case 1: row=2; break;
				case 2: row=2; break;
				case 3: row=1; break;
				case 4: row=1; break;
				case 5: row=0; break;
				case 6: row=0; break;
				default:row=-1;break;
				}
//				if (isZ[layer]==1){
//					this.getDetectorCanvas().getCanvas("Occupancy Z").cd(column + numberOfColumns * row);
//					this.getDetectorCanvas().getCanvas("Occupancy Z").draw(
//						this.getDataGroup().getItem(0, 0, 0).getH1F("Hitmap : Layer " + layer + " Sector " + sector));
//				}else{
//					this.getDetectorCanvas().getCanvas("Occupancy C").cd(column + numberOfColumns * row);
//					this.getDetectorCanvas().getCanvas("Occupancy C").draw(
//						this.getDataGroup().getItem(0, 0, 0).getH1F("Hitmap : Layer " + layer + " Sector " + sector));
//				}
				
				switch (layer) {
				case 1: row=5; break;
				case 2: row=2; break;
				case 3: row=1; break;
				case 4: row=4; break;
				case 5: row=0; break;
				case 6: row=3; break;
				default:row=-1;break;
				}
////				this.getDetectorCanvas().getCanvas("Occupancies").cd(column + numberOfColumns * row);
////				this.getDetectorCanvas().getCanvas("Occupancies").draw(
////						this.getDataGroup().getItem(sector, layer, 0).getH1F("Hitmap : Layer " + layer + " Sector " + sector));
////				
//				this.getDetectorCanvas().getCanvas("Occupancy").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("Occupancy").draw(
//						this.getDataGroup().getItem(0, 0, 0).getH1F("Hitmap : Layer " + layer + " Sector " + sector));
//
				this.getDetectorCanvas().getCanvas("NbHits vs Time").cd(column + numberOfColumns * row);
				this.getDetectorCanvas().getCanvas("NbHits vs Time").draw(
						this.getDataGroup().getItem(0, 0, 0).getH1F("NbHits vs Time : Layer " + layer + " Sector " + sector));
//				
//				this.getDetectorCanvas().getCanvas("MaxADC").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("MaxADC").draw(
//						this.getDataGroup().getItem(0, 0, 1).getH1F("ADCMax : Layer " + layer + " Sector " + sector));
//
//				this.getDetectorCanvas().getCanvas("MaxADC vs Strip").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("MaxADC vs Strip").draw(
//						this.getDataGroup().getItem(0, 0, 1).getH1F("ADCMax vs Strip : Layer " + layer + " Sector " + sector));
//				
//				this.getDetectorCanvas().getCanvas("IntegralPulse").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("IntegralPulse").draw(
//						this.getDataGroup().getItem(0, 0, 1).getH1F("IntegralPulse : Layer " + layer + " Sector " + sector));
//
//				this.getDetectorCanvas().getCanvas("IntegralPulse vs Strip").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("IntegralPulse vs Strip").draw(
//						this.getDataGroup().getItem(0, 0, 1).getH1F("IntegralPulse vs Strip : Layer " + layer + " Sector " + sector));
//				
//				this.getDetectorCanvas().getCanvas("TimeMax").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("TimeMax").draw(
//						this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfMax : Layer " + layer + " Sector " + sector));
//
//				this.getDetectorCanvas().getCanvas("TimeMax vs Strip").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("TimeMax vs Strip").draw(
//						this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfMax vs Strip : Layer " + layer + " Sector " + sector));
//				
//				this.getDetectorCanvas().getCanvas("ToT").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("ToT").draw(
//						this.getDataGroup().getItem(0, 0, 2).getH1F("ToT : Layer " + layer + " Sector " + sector));
//				
//				this.getDetectorCanvas().getCanvas("ToT per strip").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("ToT per strip").draw(
//						this.getDataGroup().getItem(0, 0, 2).getH1F("ToT per strip : Layer " + layer + " Sector " + sector));
//				
//				this.getDetectorCanvas().getCanvas("FToT").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("FToT").draw(
//						this.getDataGroup().getItem(0, 0, 2).getH1F("FToT : Layer " + layer + " Sector " + sector));
//				
//				this.getDetectorCanvas().getCanvas("FToT per strip").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("FToT per strip").draw(
//						this.getDataGroup().getItem(0, 0, 2).getH1F("FToT per strip : Layer " + layer + " Sector " + sector));
//
//				this.getDetectorCanvas().getCanvas("OccupancyClusters").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("OccupancyClusters").draw(
//						this.getDataGroup().getItem(0, 0, 0).getH1F("HitmapClusters : Layer " + layer + " Sector " + sector));
//
//				this.getDetectorCanvas().getCanvas("NbClusters vs Time").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("NbClusters vs Time").draw(
//						this.getDataGroup().getItem(0, 0, 0).getH1F("NbClusters vs Time : Layer " + layer + " Sector " + sector));
//				
//				this.getDetectorCanvas().getCanvas("ClusterCharge").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("ClusterCharge").draw(
//						this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterCharge : Layer " + layer + " Sector " + sector));
//
//				this.getDetectorCanvas().getCanvas("ClusterCharge per strip").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("ClusterCharge per strip").draw(
//						this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterCharge per strip : Layer " + layer + " Sector " + sector));
//				
//				this.getDetectorCanvas().getCanvas("ClusterSize").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("ClusterSize").draw(
//						this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterSize : Layer " + layer + " Sector " + sector));
//
//				this.getDetectorCanvas().getCanvas("ClusterSize per strip").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("ClusterSize per strip").draw(
//						this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterSize per strip : Layer " + layer + " Sector " + sector));
//
//				this.getDetectorCanvas().getCanvas("Residuals").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("Residuals").draw(
//						this.getDataGroup().getItem(0, 0, 3).getH1F("Residuals : Layer " + layer + " Sector " + sector));
//				
//				this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid").draw(
//						this.getDataGroup().getItem(0, 0, 1).getH1F("MaxAdcOfCentroid : Layer " + layer + " Sector " + sector));
//				
//				this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid per strip").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid per strip").draw(
//						this.getDataGroup().getItem(0, 0, 1).getH1F("MaxAdcOfCentroid per strip : Layer " + layer + " Sector " + sector));
//
//				this.getDetectorCanvas().getCanvas("TimeOfCentroid").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("TimeOfCentroid").draw(
//						this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfCentroid : Layer " + layer + " Sector " + sector));
//				
//				this.getDetectorCanvas().getCanvas("TimeOfCentroid per strip").cd(column + numberOfColumns * row);
//				this.getDetectorCanvas().getCanvas("TimeOfCentroid per strip").draw(
//						this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfCentroid per strip : Layer " + layer + " Sector " + sector));
//				
			}
	}
//		this.getDetectorCanvas().getCanvas("Occupancies").update();
//		this.getDetectorCanvas().getCanvas("Occupancy").update();
//		this.getDetectorCanvas().getCanvas("Occupancy C").update();
//		this.getDetectorCanvas().getCanvas("Occupancy Z").update();
//		this.getDetectorCanvas().getCanvas("NbHits vs Time").update();
//
//		this.getDetectorCanvas().getCanvas("MaxADC").update();
//		this.getDetectorCanvas().getCanvas("MaxADC vs Strip").update();
//		this.getDetectorCanvas().getCanvas("IntegralPulse").update();
//		this.getDetectorCanvas().getCanvas("IntegralPulse vs Strip").update();
//
//		this.getDetectorCanvas().getCanvas("TimeMax").update();
//		this.getDetectorCanvas().getCanvas("TimeMax vs Strip").update();
//		this.getDetectorCanvas().getCanvas("ToT").update();
//		this.getDetectorCanvas().getCanvas("ToT per strip").update();
//		this.getDetectorCanvas().getCanvas("FToT").update();
//		this.getDetectorCanvas().getCanvas("FToT per strip").update();
//
//		this.getDetectorCanvas().getCanvas("OccupancyClusters").update();
//		this.getDetectorCanvas().getCanvas("NbClusters vs Time").update();
//		this.getDetectorCanvas().getCanvas("ClusterCharge").update();
//		this.getDetectorCanvas().getCanvas("ClusterCharge per strip").update();
//		this.getDetectorCanvas().getCanvas("ClusterSize").update();
//		this.getDetectorCanvas().getCanvas("ClusterSize per strip").update();
//		this.getDetectorCanvas().getCanvas("Residuals").update();
//		this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid").update();
//		this.getDetectorCanvas().getCanvas("MaxAdcOfCentroid per strip").update();
//		this.getDetectorCanvas().getCanvas("TimeOfCentroid").update();
//		this.getDetectorCanvas().getCanvas("TimeOfCentroid per strip").update();
		
	}
	
	/**
	 * Read 1 event and fill histograms 
	 */
	public void processEvent(DataEvent event) {
		try {
			
			if (this.getNumberOfEvents() >= super.eventResetTime_current[0] && super.eventResetTime_current[0] > 0){
				resetEventListener();
			}
			//System.out.println("Mask: "+getTriggerMask());
			if (!testTriggerMask()) return;
			
			count ++;
			if (this.getNumberOfEvents()%1000==0){
				System.out.println("");
				System.out.println("BMT event: " + count + " / since last reset: "+ this.getNumberOfEvents());
			}
//			System.out.println("Event: " + count/*this.getNumberOfEvents()*/);
			//		if (this.getNumberOfEvents()>=30000){
			//			resetEventListener();
			//		}
			this.pulseViewer.clearHits(); /* Remove all hits from previous event*/

			
			/* ===== RUN RECONSTRUCTION ===== */
//			recoCo.setFieldsConfig("SOLENOID80TORUS30");
//			recoCo.processDataEvent(event);
//			System.out.println("Reconstruction done ");

			//event.show();
		
			/* ===== READ DECODED BANK ===== */
//			if (event.hasBank("RUN::config") == true) {
//				DataBank bank = event.getBank("RUN::config");
//				bank.show();
//			}
			
			int multiplicity[][] = new int[numberOfSectors + 1][numberOfLayers + 1];
			Event currentEvent = new Event();
			
			if (event.hasBank("BMT::adc") == true) {
				DataBank bank = event.getBank("BMT::adc");
				//bank.show();
				this.getDataGroup().getItem(0, 0, 0).getH1F("hitMultiplicity").fill(bank.rows(),1);

				//if (bank.rows()>4){ //CUT

				for (int i = 0; i < bank.rows(); i++) { /* For all hits */
					//System.out.println("Hit: "+(i+1)+"/"+bank.rows());
					int sector = bank.getByte("sector", i);
					int layer = bank.getByte("layer", i);
					int component = bank.getShort("component", i);
					float timeOfMax = bank.getFloat("time", i);
					int integralOverPulse = bank.getInt("integral", i);
					float adcOfMax = bank.getInt("ADC", i);
					int adcOfPulse[] = new int[numberOfSamples];
					long timestamp = bank.getLong("timestamp",i);

					for (int j = 0; j < numberOfSamples; j++) {
						adcOfPulse[j]=/*0;//*/bank.getInt("bin"+j,i);
						//pulseHistoBMT.setBinContent(j,adcOfPulse[j]);
						//System.out.println("  bin : "+j+"  adc : "+adcOfPulse[j]);
					}
					//System.out.println("Sector: "+sector+" Layer: "+layer+" Component: "+component);
					//System.out.println("Sector: "+sector+" Layer: "+layer+" Component: "+component+ " TimeOfMax: "+timeOfMax+" AdcOfMax: "+adcOfMax);
					 if (component == -1){
						 continue;
					 }

					/* ===== FILL EVENT =====*/
					 
					Hit currentHit = new Hit(i, sector,layer,component);
					currentEvent.addHits(currentHit);
					 
					/* ===== APPLY CUTS ===== */

					if ((!mask[sector][layer][component])/*||(timeOfMax < 80)||(timeOfMax > 160)*/){
						continue;
					}

					/* ===== COMPUTE GENERAL QUANTITIES ===== */

					multiplicity[sector][layer]++;
					numberOfHitsPerStrip[sector][layer][component]++;
					//				offset[layer][sector][component]=256;
					//				threshold[layer][sector][component]=5*8.5;
					int dream=0;
					int dreamLayer = 0;
					for (int layerNb=1; layerNb<layer; layerNb++){
						dreamLayer = dreamLayer + numberOfSectors * numberOfChips[layerNb];
					}
					int dreamSector = (sector-1) * numberOfChips[layer] ;
					int dreamTile = (component - 1) / numberOfStripsPerChip + 1;
					dream = dreamLayer + dreamSector + dreamTile;
					numberOfHitsPerDream[dream]++;

					/* ===== FILL OCCUPANCY PLOTS ===== */

					this.getDataGroup().getItem(0, 0, 0).getH1F("Hitmap : Layer " + layer + " Sector " + sector).fill(component, 1);
					this.getDataGroup().getItem(0, 0, 0).getH2F("Occupancies").fill(component,3*(layer-1)+(sector-1),1);
					//System.out.println("Sector: "+sector+" Layer: "+layer+" Component: "+component);
					
					int eventBin = this.getNumberOfEvents()/ratePlotScale;
					this.getDataGroup().getItem(0, 0, 0).getH1F("NbHits vs Time : Layer " + layer + " Sector " + sector).fill(eventBin);
					int numberOfBins=100;
					int rescaleFactor=2;
					if (eventBin > (numberOfBins-numberOfBins*10/100)){
						ratePlotScale*=rescaleFactor;
						double hitNbVSEvents[][][] = new double[numberOfSectors + 1][numberOfLayers + 1][numberOfBins];
						for (int sectorCounter = 1; sectorCounter <= numberOfSectors; sectorCounter++) {
							for (int layerCounter = 1; layerCounter <= numberOfLayers; layerCounter++) {
								for (int eventBinCounter=0; eventBinCounter<numberOfBins; eventBinCounter++){
									hitNbVSEvents[sectorCounter][layerCounter][eventBinCounter]=this.getDataGroup().getItem(0, 0, 0).getH1F("NbHits vs Time : Layer " + layerCounter + " Sector " + sectorCounter).getBinContent(eventBinCounter);					
									this.getDataGroup().getItem(0, 0, 0).getH1F("NbHits vs Time : Layer " + layerCounter + " Sector " + sectorCounter).setBinContent(eventBinCounter,0);
								}
								this.getDataGroup().getItem(0, 0, 0).getH1F("NbHits vs Time : Layer " + layerCounter + " Sector " + sectorCounter).setTitleX("Events (Each bin is "+ratePlotScale+" events)");
								updateHistoChanges();

								for (int eventBinCounter=0; eventBinCounter<numberOfBins/rescaleFactor; eventBinCounter++){
									for (int binAdding=0; binAdding<rescaleFactor; binAdding++){
										this.getDataGroup().getItem(0, 0, 0).getH1F("NbHits vs Time : Layer " + layerCounter + " Sector " + sectorCounter).fill(eventBinCounter,hitNbVSEvents[sectorCounter][layerCounter][rescaleFactor*eventBinCounter+binAdding]);
									}
								}
							}
						}
					}
					
					/* ===== FILL ADC PLOTS ===== */

					this.getDataGroup().getItem(0, 0, 1).getH1F("ADCMax : Layer " + layer + " Sector " + sector).fill(adcOfMax);
					double adcMaxOldAvg = this.getDataGroup().getItem(0, 0, 1).getH1F("ADCMax vs Strip : Layer " + layer + " Sector " + sector).getBinContent(component);
					this.getDataGroup().getItem(0, 0, 1).getH1F("ADCMax vs Strip : Layer " + layer + " Sector " + sector).setBinContent(component, adcMaxOldAvg + (adcOfMax-adcMaxOldAvg)/numberOfHitsPerStrip[sector][layer][component]);
					//System.out.println("Sector: "+sector+" Layer: "+layer+" Component: "+component+" Max: "+ adcOfMax);

					this.getDataGroup().getItem(0, 0, 1).getH1F("IntegralPulse : Layer " + layer + " Sector " + sector).fill(integralOverPulse);
					double integralOldAvg = this.getDataGroup().getItem(0, 0, 1).getH1F("IntegralPulse vs Strip : Layer " + layer + " Sector " + sector).getBinContent(component);
					this.getDataGroup().getItem(0, 0, 1).getH1F("IntegralPulse vs Strip : Layer " + layer + " Sector " + sector).setBinContent(component, integralOldAvg + (integralOverPulse-integralOldAvg)/numberOfHitsPerStrip[sector][layer][component]);
					//System.out.println("Sector: "+sector+" Layer: "+layer+" Component: "+component+" Integral: "+integralOverPulse);

					/* ===== FILL TIME PLOTS ===== */

//					int max = 0;
//					int timeMax = 0;
//					for (int j = 0; j < numberOfSamples; j++) {
//						if (adcOfPulse[j]>max){
//							max = adcOfPulse[j];
//							timeMax = j;
//						}
//						timeOfMax = timeMax *40 +20;
//						//pulseHistoBMT.setBinContent(j,adcOfPulse[j]);
//						//System.out.println("  bin : "+j+"  adc : "+adcOfPulse[j]);
//					}
					
					if (timeOfMax < samplingTime || timeOfMax > samplingTime*(numberOfSamples*(1+sparseReading)-1-1)){
						
					}else{
						this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfMax cut : Layer " + layer + " Sector " + sector).fill(timeOfMax);		
					}
					this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfMax : Layer " + layer + " Sector " + sector).fill(timeOfMax);
					this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfMax no fit : Layer " + layer + " Sector " + sector).fill(Math.round( timeOfMax/samplingTime )*samplingTime);		
					
					double timeOldAvg = this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfMax vs Strip : Layer " + layer + " Sector " + sector).getBinContent(component);
					this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfMax vs Strip : Layer " + layer + " Sector " + sector).setBinContent(component, timeOldAvg + (timeOfMax-timeOldAvg)/numberOfHitsPerStrip[sector][layer][component]);
					//System.out.println("Sector: "+sector+" Layer: "+layer+" Component: "+component+ "TimeOfMax: "+timeOfMax);

					double timeMaxDreamOldAvg = this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfMax").getBinContent(dream);
					this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfMax").setBinContent(dream,timeMaxDreamOldAvg + (timeOfMax-timeMaxDreamOldAvg)/numberOfHitsPerDream[dream]);
					//System.out.println("Sector: "+sector+" Layer: "+layer+" Component: "+component+ "Dream: "+dream+"TimeOfMax:"+timeOfMax);

					int ftot=-1; /*First time over threshold */
					int ltot=-1; /*Last time over threshold */
					int tot=-1;  /*Time over threshold*/
					for (int j = 0; j < numberOfSamples; j++) {
						if (adcOfPulse[j]>=(adcOffset+noise*sigmaThreshold)&&(ftot==-1)){
							ftot=j;
						}else if ((adcOfPulse[j]<=(adcOffset+noise*sigmaThreshold))&&(ftot!=-1)&&(tot==-1)){
							ltot=j;
							tot = ltot-ftot;
						}
					}
					if (ftot>=0){
						this.getDataGroup().getItem(0, 0, 2).getH1F("FToT : Layer " + layer + " Sector " + sector).fill(ftot);
						double ftotOldAvg = this.getDataGroup().getItem(0,0,2).getH1F("FToT per strip : Layer " + layer + " Sector " + sector).getBinContent(component);
						this.getDataGroup().getItem(0, 0, 2).getH1F("FToT per strip : Layer " + layer + " Sector " + sector).setBinContent(component, ftotOldAvg + (ftot-ftotOldAvg)/numberOfHitsPerStrip[sector][layer][component]);
					}
					if (tot>=0){
						this.getDataGroup().getItem(0, 0, 2).getH1F("ToT : Layer " + layer + " Sector " + sector).fill(tot);
						double totOldAvg = this.getDataGroup().getItem(0,0,2).getH1F("ToT per strip : Layer " + layer + " Sector " + sector).getBinContent(component);
						this.getDataGroup().getItem(0, 0, 2).getH1F("ToT per strip : Layer " + layer + " Sector " + sector).setBinContent(component, totOldAvg + (tot-totOldAvg)/numberOfHitsPerStrip[sector][layer][component]);
					}
					//System.out.println("Sector: "+sector+" Layer: "+layer+" Component: "+component+"Time Max: "+timeOfMax+" ftot: "+ftot+" tot: "+tot);

					/* ===== PULSE VIEWER ===== */

					int [] hit = new int [numberOfSamples+3];
					hit[0]=sector;
					hit[1]=layer;
					hit[2]=component;
					for (int j = 0; j < numberOfSamples; j++) {
						hit[j+3]=adcOfPulse[j];
					}
					this.pulseViewer.add(hit);

				} /* End of hits loop */
				
				if (this.pulseViewer.pulseStatus==true){
					this.pulseViewer.updateComboBox();
				}
				//System.out.println("Pulse Done");
				
				for (int sector = 1; sector <= numberOfSectors; sector++) {
					for (int layer = 1; layer <= numberOfLayers; layer++) {
						if (multiplicity[sector][layer]>0){
							this.getDataGroup().getItem(0, 0, 0).getH1F("Multiplicity : Layer " + layer + " Sector " + sector).fill(multiplicity[sector][layer]);
							double occupancy = (100*multiplicity[sector][layer]/(numberOfStrips[layer]+0.0));
							this.getDataGroup().getItem(0, 0, 0).getH1F("TileOccupancy : Layer " + layer + " Sector " + sector).fill(occupancy);
						}
					}
				}

			} /* End of if event has BMT::adc bank loop */

			currentEvent.clustering();
			
			/* ===== FILL SMTHG ===== */
			
			for (int sector = 1; sector <= numberOfSectors; sector++) {
				for (int layer = 1; layer <= numberOfLayers; layer++) {
					this.getDataGroup().getItem(0, 0, 0).getH1F("Cluster Multiplicity : Layer " + layer + " Sector " + sector).fill(currentEvent.getClusterNumber(sector, layer));
				}
			}
//			if (this.getNumberOfEvents()%5000==1 && this.getNumberOfEvents()>1000){
//				for (int sector = 1; sector <= numberOfSectors; sector++) {
//					for (int layer = 1; layer <= numberOfLayers; layer++) {
//						double clustMultiplicity = this.getDataGroup().getItem(0, 0, 0).getH1F("Cluster Multiplicity : Layer " + layer + " Sector " + sector).getMean();
//						System.out.println("BMT multiplicity sector "+sector+" layer "+layer+" : "+clustMultiplicity);
//					}
//				}
//				for (int sector = 1; sector <= numberOfSectors; sector++) {
//					for (int layer = 1; layer <= numberOfLayers; layer++) {
//						double clustMultiplicity = this.getDataGroup().getItem(0, 0, 0).getH1F("Cluster Multiplicity : Layer " + layer + " Sector " + sector).getMean();
//						System.out.println(clustMultiplicity);
//					}
//				}
//			}
//			System.out.println("Count: "+count);
			if (this.getNumberOfEvents()%100 == 1){
//				System.out.println("*** BMT");
				double mean=0; int numberOfPoints=0; double occupancySumTot=0;
				for (int layer = 1; layer <= numberOfLayers; layer++) {
					double meanLayer=0; int numberOfPointsLayer=0; double occupancySum=0;
					for (int sector = 1; sector <= numberOfSectors; sector++) {
						for (int component = 1; component <= numberOfStrips[layer]; component++){
							if ( ! ( (sector == 3 && layer == 5) || (sector == 1 && layer == 5) ) ){
								double OccupancyNew = this.getDataGroup().getItem(0, 0, 0).getH1F("Hitmap : Layer " + layer + " Sector " + sector).getBinContent(component);
								//							System.out.println("Occupancy : "+OccupancyNew);
								//							System.out.println("Average : "+OccupancyNew/count);
								this.getDataGroup().getItem(0, 0, 0).getH1F("OccupancyStrip : Layer " + layer + " Sector " + sector).setBinContent(component, 100*OccupancyNew/count);
								//							this.getDataGroup().getItem(0, 0, 0).getH1F("OccupancyStrip : Layer " + layer + " Sector " + sector).setBinContent(component, OccupancyOldAvg + (1-OccupancyOldAvg)/numberOfHitsPerStrip[sector][layer][component]);
								
								mean += 100*OccupancyNew/count;
								numberOfPoints++;
								meanLayer += 100*OccupancyNew/count;
								numberOfPointsLayer++;
//								System.out.println("Sector: "+sector+" Layer: "+layer+" Component: "+component+" hitNumber: "+OccupancyNew+" numberOfEvents: "+count+" Occupancy(%): "+(100*OccupancyNew/count));
								occupancySum += OccupancyNew;
								occupancySumTot += OccupancyNew;
							}
						}
					}
//					System.out.println("Occupancy BMT average layer "+layer+" : ");
//					System.out.println(meanLayer/numberOfPointsLayer);
////					System.out.println("Number of hits: "+occupancySum);
////					System.out.println(" / nb strips * nb events: "+(numberOfPointsLayer*count));
//					System.out.println(occupancySum);
//					System.out.println((numberOfPointsLayer*count));
//					System.out.println("");
////					System.out.println("Ratio: "+occupancySum/(numberOfPointsLayer*count));
				}
//				System.out.println("Occupancy BMT average total : ");
//				System.out.println(mean/numberOfPoints);
//				
////				System.out.println("Number of hits: "+occupancySumTot);
////				System.out.println(" / nb strips * nb events: "+(numberOfPoints*count));
//				System.out.println(occupancySumTot);
//				System.out.println((numberOfPoints*count));
//				
////				System.out.println("Ratio: "+occupancySumTot/(numberOfPoints*count));
//				
//				System.out.println("");
//				System.out.println("");
			}
			
			/* ===== READ RECONSTRUCTED BANK ===== */

			if (event.hasBank("BMTRec::Clusters") == true) {
				
				DataBank bankClusters = event.getBank("BMTRec::Clusters");
//				bankClusters.show();
				
//				double[][] ClusterList;
//				ClusterList = new double[bankClusters.rows()][4];
				
				for (int i = 0; i < bankClusters.rows(); i++) {

					int sectorCentroid = bankClusters.getByte("sector", i);
					int layerCentroid = bankClusters.getByte("layer", i);
					float centroid = bankClusters.getFloat("centroid", i);
					float etot = bankClusters.getFloat("ETot", i);
					short size = bankClusters.getShort("size" ,i);
					short trkID = bankClusters.getShort("trkID", i);
					//System.out.println("Sector: "+sectorCentroid+" Layer: "+layerCentroid+" Component: "+centroid);

//					ClusterList[i][0] = sectorCentroid;
//					ClusterList[i][1] = layerCentroid;
//					ClusterList[i][2] = centroid;
//					ClusterList[i][3] = size;
//					
//					boolean clusterAround = false;
//					for (int previousClusters = 0; previousClusters < i; previousClusters++) {
//						if (ClusterList[i][0]==ClusterList[previousClusters][0] && ClusterList[i][1]==ClusterList[previousClusters][1] && Math.abs(ClusterList[i][2]+ClusterList[i][3]/2-ClusterList[previousClusters][2]-ClusterList[previousClusters][3]/2)<25){
//							clusterAround =true;
//						}
//					}
//					if (clusterAround){
//						continue;
//					}
					
					
					
					/* ===== COMPUTE GENERAL QUANTITIES ===== */

					int centroidInt = Math.round(centroid);
					if ((centroidInt > numberOfStrips[layerCentroid])||(centroidInt < 1)){
						//System.out.println("avoid");
						continue;
					}
					numberOfCentroidsPerStrip[sectorCentroid][layerCentroid][centroidInt]++;

					/* ===== FILL HISTOS ===== */

					this.getDataGroup().getItem(0, 0, 0).getH1F("HitmapClusters : Layer " + layerCentroid + " Sector " + sectorCentroid).fill(centroidInt);
					int timeBin=20000; /* For Cosmics with SVT trigger 20000 is ~ an hour */
					int hour = this.getNumberOfEvents()/timeBin;
					int rescale=1;
					//				hitNbVST[layer][sector][hour]++;
					//				if (hour>100&&hour%100==0){ /* Try automatic rescaling */
					//					rescale++;
					//					System.out.println("scale : "+rescale);
					//					for (int sector2 = 1; sector2 <= maxNumberSector; sector2++) {
					//						for (int layer2 = 1; layer2 <= maxNumberLayer; layer2++) {
					//							for (int hour2=0; hour2<rescale*100; hour2++){
					//								this.getDataGroup().getItem(sector2, layer2, 4).getH1F("NbHits vs Time : Layer " + layer + " Sector " + sector).fill(hour2/rescale, hitNbVST[layer][sector][hour]);
					//							}
					//						}
					//					}
					//				}
					this.getDataGroup().getItem(0, 0, 0).getH1F("NbClusters vs Time : Layer " + layerCentroid + " Sector " + sectorCentroid).fill(hour/rescale);

					this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterCharge : Layer " + layerCentroid + " Sector " + sectorCentroid).fill(etot);
					double clusterChargeOldAvg = this.getDataGroup().getItem(0,0,1).getH1F("ClusterCharge per strip : Layer " + layerCentroid + " Sector " + sectorCentroid).getBinContent(centroidInt);
					this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterCharge per strip : Layer " + layerCentroid + " Sector " + sectorCentroid).setBinContent(centroidInt, clusterChargeOldAvg + (etot-clusterChargeOldAvg)/numberOfCentroidsPerStrip[sectorCentroid][layerCentroid][centroidInt]);
					//System.out.println("Sector: "+sector+" Layer: "+layer+" Component: "+component+" Cluster charge: "+etot);

					this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterSize : Layer " + layerCentroid + " Sector " + sectorCentroid).fill(size);
					double clusterSizeOldAvg = this.getDataGroup().getItem(0,0,1).getH1F("ClusterSize per strip : Layer " + layerCentroid + " Sector " + sectorCentroid).getBinContent(centroidInt);
					this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterSize per strip : Layer " + layerCentroid + " Sector " + sectorCentroid).setBinContent(centroidInt, clusterSizeOldAvg + (size-clusterSizeOldAvg)/numberOfCentroidsPerStrip[sectorCentroid][layerCentroid][centroidInt]);
					//System.out.println("Sector: "+sector+" Layer: "+layer+" Component: "+component+" Cluster size: "+size);

					if (trkID!=-1){
						this.getDataGroup().getItem(0, 0, 0).getH1F("OccupancyReco : Layer " + layerCentroid + " Sector " + sectorCentroid).fill(centroidInt, 1);
					}
					
					/* ===== FILL HIT/CLUSTERS HISTOS ===== */

					if (event.hasBank("BMT::adc") == true) {
						DataBank bankAdc = event.getBank("BMT::adc");
						//bankAdc.show();
						int hit = 0;
						int sector = 0;//bankAdc.getByte("sector", hit);
						int layer = 0;//bankAdc.getByte("layer", hit);
						int component = 0;//bankAdc.getShort("component", hit);
						float adcOfMax = 0;//bankAdc.getInt("ADC", hit);
						float timeOfMax =0;

						int nearestStrip=0;
						float adc=0;
						float time=0;

						while ( (component != centroidInt || sector != sectorCentroid || layer != layerCentroid) && hit<bankAdc.rows() ){
							sector = bankAdc.getByte("sector", hit);
							layer = bankAdc.getByte("layer", hit);
							component = bankAdc.getShort("component", hit);
							adcOfMax = bankAdc.getInt("ADC", hit);
							timeOfMax = bankAdc.getFloat("time", i);
							if ( sector == sectorCentroid && layer == layerCentroid && Math.abs(component-centroid)<2 && Math.abs(component-centroid)<Math.abs(nearestStrip-centroidInt) ){
								nearestStrip=component;
								adc = adcOfMax;
								time = timeOfMax;
							}
							hit++;
						}
						if (component == centroidInt && sector == sectorCentroid && layer == layerCentroid){
							adc=adcOfMax;
							time=timeOfMax;
							numberOfCentroidsMatchedPerStrip[sectorCentroid][layerCentroid][centroidInt]++;
							this.getDataGroup().getItem(0, 0, 1).getH1F("MaxAdcOfCentroid : Layer " + layerCentroid + " Sector " + sectorCentroid).fill(adc);
							double maxAdcOfCentroidOldAvg = this.getDataGroup().getItem(0,0,1).getH1F("MaxAdcOfCentroid per strip : Layer " + layerCentroid + " Sector " + sectorCentroid).getBinContent(centroidInt);
							this.getDataGroup().getItem(0, 0, 1).getH1F("MaxAdcOfCentroid per strip : Layer " + layerCentroid + " Sector " + sectorCentroid).setBinContent(centroidInt, maxAdcOfCentroidOldAvg + (adc-maxAdcOfCentroidOldAvg)/numberOfCentroidsPerStrip[sectorCentroid][layerCentroid][centroidInt]);
							//System.out.println("Matching Sector: "+sectorCentroid+" Layer: "+layerCentroid+" Component: "+centroid+ " Max adc of centroid: "+adc);	

							this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfCentroid : Layer " + layerCentroid + " Sector " + sectorCentroid).fill(time);
							double timeOfCentroidOldAvg = this.getDataGroup().getItem(0,0,2).getH1F("TimeOfCentroid per strip : Layer " + layerCentroid + " Sector " + sectorCentroid).getBinContent(centroidInt);
							this.getDataGroup().getItem(0, 0, 2).getH1F("TimeOfCentroid per strip : Layer " + layerCentroid + " Sector " + sectorCentroid).setBinContent(centroidInt, timeOfCentroidOldAvg + (time-timeOfCentroidOldAvg)/numberOfCentroidsPerStrip[sectorCentroid][layerCentroid][centroidInt]);
						}
						//					else{
						//						System.out.println("ERROR : Sector: "+sectorCentroid+" Layer: "+layerCentroid+" Component: "+centroid);
						//						System.out.println("nearestStrip: "+nearestStrip);
						//					}
					}	
				}
			} /* End of if event has BMTRec::Clusters bank loop */

			if (event.hasBank("BMT::adc") == true) {			
				DataBank bankAdc = event.getBank("BMT::adc");
//				bankAdc.show();
			}
			
			if (event.hasBank("BMTRec::Clusters") == true) {			
				DataBank bankRecClust = event.getBank("BMTRec::Clusters");
//				bankRecClust.show();
			}
//			event.show();
//			if (event.hasBank("BMTRec::Clusters") == true) {			
//				DataBank bankCosmics = event.getBank("BMTRec::Clusters");
//				bankCosmics.show();
//			}
//			if (event.hasBank("CVTRec::Cosmics") == true) {			
//				DataBank bankCosmics = event.getBank("CVTRec::Cosmics");
//				bankCosmics.show();
//			}
//			if (event.hasBank("Rec::Particle") == true) {			
//				DataBank bankRecPart = event.getBank("Rec::Particle");
//				bankRecPart.show();
//			}
//			if (event.hasBank("CVTRec::Tracks") == true) {			
//				DataBank bankRecTracks = event.getBank("CVTRec::Tracks");
//				bankRecTracks.show();
//			}
//			if (event.hasBank("CVTRec::Trajectory") == true) {	
//				DataBank bankTrajectory = event.getBank("CVTRec::Trajectory");
//				bankTrajectory.show();
//			}
			
//			event.show();
			
			if (event.hasBank("CVTRec::Tracks") == true) {			
				DataBank bankTracks = event.getBank("CVTRec::Tracks");
//				bankTracks.show();
				for (int trackNb = 0; trackNb < bankTracks.rows(); trackNb++) { /* For all tracks */
					int trackID=bankTracks.getShort("ID", trackNb);
					float trackChi2 = bankTracks.getFloat("chi2", trackNb);
					
					float chi2Max = 200;
					if (trackChi2>chi2Max){
//						System.out.println("Not acceptable track (Chi2 too large)");
						continue;
					}
//					System.out.println("Acceptable track");
					
					boolean expectedTrackLayer[];
					boolean expectedTrackTile[][];
					expectedTrackLayer = new boolean[numberOfLayers + 1];
					expectedTrackTile = new boolean[numberOfSectors + 1][numberOfLayers + 1];
					for (int layer = 1; layer <= numberOfLayers; layer++) {
						expectedTrackLayer[layer]=false;
						for (int sector = 1; sector <= numberOfSectors; sector++) {
							expectedTrackTile[sector][layer]=false;
						}
					}
					
					boolean foundTrackLayer[];
					boolean foundTrackTile[][];
					foundTrackLayer = new boolean[numberOfLayers + 1];
					foundTrackTile = new boolean[numberOfSectors + 1][numberOfLayers + 1];
					for (int layer = 1; layer <= numberOfLayers; layer++) {
						foundTrackLayer[layer]=false;
						for (int sector = 1; sector <= numberOfSectors; sector++) {
							foundTrackTile[sector][layer]=false;
						}
					}
					
					boolean gotCluster = false;
					
					if (event.hasBank("CVTRec::Trajectory") == true) {
						DataBank bankTrajectory = event.getBank("CVTRec::Trajectory");
//						bankTrajectory.show();
						for (int hitExpNb = 0; hitExpNb < bankTrajectory.rows(); hitExpNb++) { /* For all hits */
//							if ((hitExpNb/12+1)!=trackID){
//								continue;
//							}
							int ID = /*(hitExpNb/12+1); //*/bankTrajectory.getInt("ID", hitExpNb);
							int globalLayer = /*(hitExpNb%12 +1); //*/bankTrajectory.getByte("LayerTrackIntersPlane", hitExpNb);
							int sector = bankTrajectory.getByte("SectorTrackIntersPlane", hitExpNb);
							float xTrack = bankTrajectory.getFloat("XtrackIntersPlane", hitExpNb);
							float yTrack = bankTrajectory.getFloat("YtrackIntersPlane", hitExpNb);
							float zTrack = bankTrajectory.getFloat("ZtrackIntersPlane", hitExpNb);
							float phiTrack = bankTrajectory.getFloat("PhiTrackIntersPlane",hitExpNb);
							float thetaTrack = bankTrajectory.getFloat("ThetaTrackIntersPlane",hitExpNb);
							float trackAngle = bankTrajectory.getFloat("trkToMPlnAngl",hitExpNb);
							float expectedCentroid = bankTrajectory.getFloat("CalcCentroidStrip",hitExpNb);
//							System.out.println("HIT     ID: "+ID+"  globalLayer:"+globalLayer+"  sector:"+sector+"  x:"+xTrack+"  y:"+yTrack+"  z:"+zTrack);
							if ( globalLayer <= 6 ) {
								continue;
							}
							if ( (Float.isNaN(xTrack)||Float.isNaN(yTrack)||Float.isNaN(zTrack)) || (xTrack==0&&yTrack==0&&zTrack==0) ) {
//								System.out.println("Not acceptable hit (Bad coordinates)");
								continue;
							}
							
							int layer=globalLayer-6;
							double phiPos=Math.atan(yTrack/xTrack);
							if (xTrack<0){
								phiPos = Math.PI+Math.atan(yTrack/xTrack);
							}else if (yTrack<0){
								phiPos = 2*Math.PI+Math.atan(yTrack/xTrack);
							}
							
//							System.out.println("phiPos: "+phiPos);
							for (int i = 0; i < 3; i++) {
								double angle_i = org.jlab.rec.cvt.bmt.Constants.getCRCEDGE1()[(layer-1)/2][i];
								double angle_f = org.jlab.rec.cvt.bmt.Constants.getCRCEDGE2()[(layer-1)/2][i];
//								System.out.println("phiPos: "+Math.toDegrees(phiPos));
//								System.out.println("angle i "+Math.toDegrees(angle_i));
//								System.out.println("angle f "+Math.toDegrees(angle_f));
					            if ((phiPos >= angle_i && phiPos <= angle_f)) {
					                sector = i+1;
//					                System.out.println(sector);
					            }
					        }
							if (sector==0){
//								System.out.println("Not acceptable hit (Between 2 sectors)");
								continue;
							}
							//System.out.println("Acceptable expected hit layer "+layer+" sector "+sector);
							expectedTrackLayer[layer]=true;
							expectedTrackTile[sector][layer]=true;
							
							
							
							if (event.hasBank("BMTRec::Clusters") == true) {
								DataBank bankClusters = event.getBank("BMTRec::Clusters");
//								bankClusters.show();
								for (int clusterNb = 0; clusterNb < bankClusters.rows(); clusterNb++) {
									int clusterTrkID = bankClusters.getShort("trkID" ,clusterNb);
									int clusterSector = bankClusters.getByte("sector", clusterNb);
									int clusterLayer = bankClusters.getByte("layer", clusterNb);
									short clusterSize = bankClusters.getShort("size" ,clusterNb);
									//System.out.println("Cluster:  trackID:"+clusterTrkID+"  sector: "+clusterSector+"  layer:"+clusterLayer);
									if ((clusterTrkID!=trackID)||(clusterSector!=sector)||(clusterLayer!=layer)){
										continue;
									}
									//System.out.println("Acceptable found cluster");
									
									foundTrackLayer[layer]=true;
									foundTrackTile[sector][layer]=true;
									gotCluster = true;
								}
							} /* End of if event has BMTRec::Clusters bank loop */
//							if (foundTrackLayer[layer]){
//								efficiencyTrackLayer[layer] = (efficiencyTrackLayer[layer] * efficiencyTrackLayerNb[layer] + 1)/(efficiencyTrackLayerNb[layer]+1);
//								efficiencyTrackLayerNb[layer]++;
//								System.out.println("A cluster found");
//							}else{
//								efficiencyTrackLayer[layer] = (efficiencyTrackLayer[layer] * efficiencyTrackLayerNb[layer] + 0)/(efficiencyTrackLayerNb[layer]+1);
//								efficiencyTrackLayerNb[layer]++;
//								System.out.println("NO cluster found");
//							}
//							if (foundTrackTile[sector][layer]){
//								efficiencyTrackTile[sector][layer] = (efficiencyTrackTile[sector][layer] * efficiencyTrackTileNb[sector][layer] + 1)/(efficiencyTrackTileNb[sector][layer]+1);
//								efficiencyTrackTileNb[sector][layer]++;
//								System.out.println("A cluster found tile");
//							}else{
//								efficiencyTrackTile[sector][layer] = (efficiencyTrackTile[sector][layer] * efficiencyTrackTileNb[sector][layer] + 0)/(efficiencyTrackTileNb[sector][layer]+1);
//								efficiencyTrackTileNb[sector][layer]++;
//								System.out.println("NO cluster found tile");
//								System.out.println("tile tot:"+efficiencyTrackTileNb[sector][layer]);
//							}								
						}
					} /* End of if event has CVTRec::Trajectory bank loop */
					for (int layerI = 1; layerI <= numberOfLayers; layerI++) {
						if (expectedTrackLayer[layerI] && foundTrackLayer[layerI] /*&& gotCluster*/){
							efficiencyTrackLayer[layerI] = (efficiencyTrackLayer[layerI] * efficiencyTrackLayerNb[layerI] + 1)/(efficiencyTrackLayerNb[layerI]+1);
							efficiencyTrackLayerNb[layerI]++;
//							System.out.println("A cluster found");
						}else if (expectedTrackLayer[layerI]){
							efficiencyTrackLayer[layerI] = (efficiencyTrackLayer[layerI] * efficiencyTrackLayerNb[layerI] + 0)/(efficiencyTrackLayerNb[layerI]+1);
							efficiencyTrackLayerNb[layerI]++;
//							System.out.println("NO cluster found");
						}
						if (count%10000==1){
//							System.out.println("Efficiency layer "+layerI+" : "+efficiencyTrackLayer[layerI]+"   ("+(efficiencyTrackLayer[layerI]*efficiencyTrackLayerNb[layerI])+"/"+efficiencyTrackLayerNb[layerI]+")");
						}
						for (int sectorI = 1; sectorI <= numberOfSectors; sectorI++) {
							if (expectedTrackTile[sectorI][layerI] && foundTrackTile[sectorI][layerI] /*&& gotCluster*/){
								efficiencyTrackTile[sectorI][layerI] = (efficiencyTrackTile[sectorI][layerI] * efficiencyTrackTileNb[sectorI][layerI] + 1)/(efficiencyTrackTileNb[sectorI][layerI]+1);
								efficiencyTrackTileNb[sectorI][layerI]++;
//								System.out.println("A cluster found tile");
							}else if (expectedTrackTile[sectorI][layerI]){
								efficiencyTrackTile[sectorI][layerI] = (efficiencyTrackTile[sectorI][layerI] * efficiencyTrackTileNb[sectorI][layerI] + 0)/(efficiencyTrackTileNb[sectorI][layerI]+1);
								efficiencyTrackTileNb[sectorI][layerI]++;
//								System.out.println("NO cluster found tile");
							}
							if (count%1000==1){
//								System.out.println("Efficiency layer "+layerI+" sector "+sectorI+" : "+efficiencyTrackTile[sectorI][layerI]+"   ("+(efficiencyTrackTile[sectorI][layerI]*efficiencyTrackTileNb[sectorI][layerI])+"/"+efficiencyTrackTileNb[sectorI][layerI]+")");
							}
						}
					}
					
				}
			} /* End of if event has CVTRec::Tracks bank loop */
			
//			for (int layerI = 1; layerI <= numberOfLayers; layerI++) {
//				if (count%1000==1){
//					System.out.println("Efficiency layer "+layerI+" : "+efficiencyTrackLayer[layerI]+"   ("+(efficiencyTrackLayer[layerI]*efficiencyTrackLayerNb[layerI])+"/"+efficiencyTrackLayerNb[layerI]+")");
//				}
//				for (int sectorI = 1; sectorI <= numberOfSectors; sectorI++) {
//					if (count%1000==1){
//						System.out.println("Efficiency layer "+layerI+" sector "+sectorI+" : "+efficiencyTrackTile[sectorI][layerI]+"   ("+(efficiencyTrackTile[sectorI][layerI]*efficiencyTrackTileNb[sectorI][layerI])+"/"+efficiencyTrackTileNb[sectorI][layerI]+")");
//					}
//				}
//			}
			
//			if (event.hasBank("BMTRec::Clusters") == true) {			
//				DataBank bankRecClust = event.getBank("BMTRec::Clusters");
//				bankRecClust.show();
//			}
//			if (event.hasBank("CVTRec::Cosmics") == true) {			
//				DataBank bankCosmics = event.getBank("CVTRec::Cosmics");
//				bankCosmics.show();
//			}
//			if (event.hasBank("Rec::Particle") == true) {			
//				DataBank bankRecPart = event.getBank("Rec::Particle");
//				bankRecPart.show();
//			}
//			if (event.hasBank("CVTRec::Tracks") == true) {			
//				DataBank bankRecTracks = event.getBank("CVTRec::Tracks");
//				bankRecTracks.show();
//			}
//			if (event.hasBank("CVTRec::Trajectory") == true) {	
//				DataBank bankTrajectory = event.getBank("CVTRec::Trajectory");
//				bankTrajectory.show();
//			}
			
			if (event.hasBank("CVTRec::Tracks") == true) {			
				DataBank bankTracks = event.getBank("CVTRec::Tracks");
//				bankTracks.show();
				
				for (int trackNb = 0; trackNb < bankTracks.rows(); trackNb++) { /* For all tracks */
					int trackID=bankTracks.getShort("ID", trackNb);
					int trackCharge=bankTracks.getByte("q", trackNb);
//					double ptot = bankTracks.getFloat("p", trackNb);
//					double pt = bankTracks.getFloat("pt", trackNb);
//					double tandip = bankTracks.getFloat("tandip", trackNb);
					
//					double pz = Math.sqrt(ptot*ptot-pt*pt);
//					double theta = Math.toDegrees(Math.acos(tandip*pt));
					
					if (trackCharge>0){
						continue;
					}
					
					
					
					if (event.hasBank("CVTRec::Trajectory") == true) {
						DataBank bankTrajectory = event.getBank("CVTRec::Trajectory");
//						bankTrajectory.show();
						for (int hitExpNb = 0; hitExpNb < bankTrajectory.rows(); hitExpNb++) { /* For all hits */
//							
							int ID = /*(hitExpNb/12+1); //*/bankTrajectory.getInt("ID", hitExpNb);
							int globalLayer = /*(hitExpNb%12 +1); //*/bankTrajectory.getByte("LayerTrackIntersPlane", hitExpNb);
							int sector = bankTrajectory.getByte("SectorTrackIntersPlane", hitExpNb);
							float xTrack = bankTrajectory.getFloat("XtrackIntersPlane", hitExpNb);
							float yTrack = bankTrajectory.getFloat("YtrackIntersPlane", hitExpNb);
							float zTrack = bankTrajectory.getFloat("ZtrackIntersPlane", hitExpNb);
							float phiTrack = bankTrajectory.getFloat("PhiTrackIntersPlane",hitExpNb);
							float thetaTrack = bankTrajectory.getFloat("ThetaTrackIntersPlane",hitExpNb);
							float trackAngle = bankTrajectory.getFloat("trkToMPlnAngl",hitExpNb);
							float expectedCentroid = bankTrajectory.getFloat("CalcCentroidStrip",hitExpNb);
//							System.out.println("HIT     ID: "+ID+"  globalLayer:"+globalLayer+"  sector:"+sector+"  x:"+xTrack+"  y:"+yTrack+"  z:"+zTrack);
							if ( globalLayer <= 6 ) {
								continue;
							}
							if ( (Float.isNaN(xTrack)||Float.isNaN(yTrack)||Float.isNaN(zTrack)) || (xTrack==0&&yTrack==0&&zTrack==0) ) {
//								System.out.println("Not acceptable hit (Bad coordinates)");
								continue;
							}
							
//							double theta = Math.toDegrees(Math.acos(zTrack/Math.sqrt(xTrack*xTrack+yTrack*yTrack+zTrack*zTrack)));
//							
//							if (theta<70 || theta>110){
//								continue;
//							}
							
							
							
							int layer=globalLayer-6;
							double phiPos=Math.atan(yTrack/xTrack);
							if (xTrack<0){
								phiPos = Math.PI+Math.atan(yTrack/xTrack);
							}else if (yTrack<0){
								phiPos = 2*Math.PI+Math.atan(yTrack/xTrack);
							}
							
							if (event.hasBank("BMTRec::Clusters") == true) {
								DataBank bankClusters = event.getBank("BMTRec::Clusters");
//								bankClusters.show();
								for (int clusterNb = 0; clusterNb < bankClusters.rows(); clusterNb++) {
									int clusterTrkID = bankClusters.getShort("trkID" ,clusterNb);
									int clusterSector = bankClusters.getByte("sector", clusterNb);
									int clusterLayer = bankClusters.getByte("layer", clusterNb);
									short clusterSize = bankClusters.getShort("size" ,clusterNb);
									
									if ((clusterTrkID!=trackID)||(clusterSector!=sector)||(clusterLayer!=layer)){
										continue;
									}
									
									int thetaTrackDegree=0;
									if (isZ[layer]==1){
										thetaTrackDegree = (int) Math.round( Math.toDegrees(phiTrack) );
										
									}else{
										thetaTrackDegree = (int) Math.round( Math.toDegrees(thetaTrack) );
										if (thetaTrack>80){
											//System.out.println("Phi > 80");
										}
									}
									
									//System.out.println("sector: "+clusterSector+"  layer: "+clusterLayer+"  phiTrack: "+thetaTrackDegree+"  clusterSize: "+clusterSize);
									
									
									int bin = this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterSize vs angle : Layer " + clusterLayer + " Sector " + clusterSector).getxAxis().getBin(thetaTrackDegree);
									
									double clusterSizeOldAvg = this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterSize vs angle : Layer " + clusterLayer + " Sector " + clusterSector).getBinContent(bin);
									double numberOfTracksOld = this.getDataGroup().getItem(0, 0, 1).getH1F("Occupancy vs angle : Layer " + clusterLayer + " Sector " + clusterSector).getBinContent(bin);
									double numberOfTracksNew = numberOfTracksOld+1;
									this.getDataGroup().getItem(0, 0, 1).getH1F("Occupancy vs angle : Layer " + clusterLayer + " Sector " + clusterSector).setBinContent(bin,numberOfTracksNew);
									//System.out.println("sector: "+clusterSector+"  layer: "+clusterLayer+"clusterSizeOldAvg: "+clusterSizeOldAvg+"  numberOfTracksNew: "+numberOfTracksNew+" bin: "+bin+" new avg: "+((clusterSizeOldAvg*(numberOfTracksNew-1)+clusterSize)/numberOfTracksNew));
									this.getDataGroup().getItem(0, 0, 1).getH1F("ClusterSize vs angle : Layer " + clusterLayer + " Sector " + clusterSector).setBinContent(bin, ((clusterSizeOldAvg*(numberOfTracksNew-1)+clusterSize)/numberOfTracksNew));
									
								}
							}
						}
					}
				}
			}
			
			
			
		} catch (Exception e){
			e.printStackTrace();
		}

//		System.out.println("BMT Analysis Done Event: " + count/*this.getNumberOfEvents()*/);
	}
	
}

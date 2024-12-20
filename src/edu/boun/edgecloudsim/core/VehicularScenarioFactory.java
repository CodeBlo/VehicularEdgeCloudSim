/*
 * Title:        EdgeCloudSim - Scenario Factory
 * 
 * Description:  Sample scenario factory providing the default
 *               instances of required abstract classes
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.core;

import edu.boun.edgecloudsim.cloud_server.CloudServerManager;
import edu.boun.edgecloudsim.cloud_server.DefaultCloudServerManager;
import edu.boun.edgecloudsim.edge_client.DefaultMobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.SampleMobileDeviceManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.DefaultMobileServerManager;
import edu.boun.edgecloudsim.edge_client.mobile_processing_unit.MobileServerManager;
import edu.boun.edgecloudsim.edge_orchestrator.*;
import edu.boun.edgecloudsim.edge_server.DefaultEdgeServerManager;
import edu.boun.edgecloudsim.edge_server.EdgeServerManager;
import edu.boun.edgecloudsim.mobility.MobilityModel;
import edu.boun.edgecloudsim.mobility.VehicularMobility;
import edu.boun.edgecloudsim.network.MM1Queue;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.network.SampleNetworkModel;
import edu.boun.edgecloudsim.task_generator.IdleActiveLoadGenerator;
import edu.boun.edgecloudsim.task_generator.LoadGeneratorModel;

public class VehicularScenarioFactory implements ScenarioFactory {
	private int numOfMobileDevice;
	private double simulationTime;
	private String orchestratorPolicy;
	private String simScenario;
	private final PredictionType predictionType;

	public VehicularScenarioFactory(int _numOfMobileDevice,
                                    double _simulationTime,
                                    String _orchestratorPolicy,
                                    String _simScenario, PredictionType predictionType){
		orchestratorPolicy = _orchestratorPolicy;
		numOfMobileDevice = _numOfMobileDevice;
		simulationTime = _simulationTime;
		simScenario = _simScenario;
        this.predictionType = predictionType;
    }
	
	@Override
	public LoadGeneratorModel getLoadGeneratorModel() {
		return new IdleActiveLoadGenerator(numOfMobileDevice, simulationTime, simScenario);
	}

	@Override
	public EdgeOrchestrator getEdgeOrchestrator() {
		return switch (predictionType) {
			case RANDOM -> new RandomEdgeOrchestrator(orchestratorPolicy, simScenario);
			case VISIT_COUNT_BASED -> new VisitCountBasedRandomEdgeOrchestrator(orchestratorPolicy, simScenario);
			case TIME_BASED -> new TimeBasedEdgeOrchestrator(orchestratorPolicy, simScenario);
		};
	}

	@Override
	public MobilityModel getMobilityModel() {
		return new VehicularMobility(numOfMobileDevice,simulationTime);
	}

	@Override
	public NetworkModel getNetworkModel() {
		return new SampleNetworkModel(numOfMobileDevice, simScenario);
	}

	@Override
	public EdgeServerManager getEdgeServerManager() {
		return new DefaultEdgeServerManager();
	}

	@Override
	public CloudServerManager getCloudServerManager() {
		return new DefaultCloudServerManager();
	}
	
	@Override
	public MobileDeviceManager getMobileDeviceManager() throws Exception {
		return new SampleMobileDeviceManager();
	}

	@Override
	public MobileServerManager getMobileServerManager() {
		return new DefaultMobileServerManager();
	}
}

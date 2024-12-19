/*
 * Title:        EdgeCloudSim - Basic Edge Orchestrator implementation
 * 
 * Description: 
 * BasicEdgeOrchestrator implements basic algorithms which are
 * first/next/best/worst/random fit algorithms while assigning
 * requests to the edge devices.
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_orchestrator;

import edu.boun.edgecloudsim.cloud_server.CloudVM;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.mobility.RoadNode;
import edu.boun.edgecloudsim.utils.AverageAccumulator;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimUtils;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.*;
import java.util.concurrent.atomic.DoubleAccumulator;

public class TimeBasedEdgeOrchestrator extends EdgeOrchestrator {
	private static final long TASK_COUNT_THRESHOLD = 20;
	private static final Double TASK_COUNT_WINDOW = 10.0; //seconds
	private int numberOfHost; //used by load balancer
	private int lastSelectedHostIndex; //used by load balancer
	private int[] lastSelectedVmIndexes; //used by each host individually
	private final Map<RoadNode, AverageAccumulator> averageRevisitTimeMap = new HashMap<>();
	private final Map<RoadNode, Double> lastVisitTimeMap = new HashMap<>();
	private final NavigableMap<Double, Map<RoadNode, Integer>> taskProcessCountMap = new TreeMap<>();

	public TimeBasedEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	@Override
	public void initialize() {
		numberOfHost=SimSettings.getInstance().getNumOfEdgeHosts();

		double simulationTime = SimSettings.getInstance().getSimulationTime();
		int taskCountWindowCount = (int) Math.ceil(simulationTime / TASK_COUNT_WINDOW);
		for (int i = 1; i < taskCountWindowCount+1; i++) {
			taskProcessCountMap.put(i * TASK_COUNT_WINDOW + SimSettings.CLIENT_ACTIVITY_START_TIME, new HashMap<>());
		}
		lastSelectedHostIndex = -1;
		lastSelectedVmIndexes = new int[numberOfHost];
		for(int i=0; i<numberOfHost; i++)
			lastSelectedVmIndexes[i] = -1;
	}

	@Override
	public int getDeviceToOffload(Task task) {

        if (simScenario.equals("SINGLE_TIER")) {
            return SimSettings.GENERIC_EDGE_DEVICE_ID;
        }
		double creationTime = task.getCreationTime();

		Location submittedLocation = task.getSubmittedLocation();
		RoadNode roadNode = submittedLocation.getConnectedRoadNode();
		taskProcessCountMap.ceilingEntry(creationTime).getValue().compute(roadNode, (k, v) -> v == null ? 1 : v + 1);

		double lastVisitTime = Objects.requireNonNullElse(lastVisitTimeMap.put(roadNode, creationTime), SimSettings.CLIENT_ACTIVITY_START_TIME);
		double timePassed = creationTime - lastVisitTime ;
		if (!averageRevisitTimeMap.containsKey(roadNode)) {
			AverageAccumulator averageAccumulator = new AverageAccumulator();
			averageAccumulator.add(timePassed);
			averageRevisitTimeMap.put(roadNode, averageAccumulator);
			return SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		averageRevisitTimeMap.get(roadNode).add(timePassed);
		double averageRevisitTime = averageRevisitTimeMap.get(roadNode).getAverage();
		double previousRevisitTime = 0;
		do {
			previousRevisitTime += averageRevisitTime;
		}while (previousRevisitTime < TASK_COUNT_WINDOW);
		double timeWindowToCheck = creationTime - previousRevisitTime;
		if (timeWindowToCheck < SimSettings.CLIENT_ACTIVITY_START_TIME) {
			return SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		int taskCount = taskProcessCountMap.ceilingEntry(timeWindowToCheck).getValue().getOrDefault(roadNode, 0);


		if (taskCount > TASK_COUNT_THRESHOLD) {
			int randomNumber = SimUtils.getRandomNumber(1, 100);
			randomNumber -= - taskCount / 10;
			if (randomNumber <= 50) {
				return SimSettings.CLOUD_DATACENTER_ID;
			}
		}

		return SimSettings.GENERIC_EDGE_DEVICE_ID;
	}
	
	@Override
	public Vm getVmToOffload(Task task, int deviceId) {
		Vm selectedVM = null;
		
		if(deviceId == SimSettings.CLOUD_DATACENTER_ID){
			//Select VM on cloud devices via Least Loaded algorithm!
			double selectedVmCapacity = 0; //start with min value
			List<Host> list = SimManager.getInstance().getCloudServerManager().getDatacenter().getHostList();
			for (int hostIndex=0; hostIndex < list.size(); hostIndex++) {
				List<CloudVM> vmArray = SimManager.getInstance().getCloudServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
	            }
			}
		}
		else if(simScenario.equals("TWO_TIER_WITH_EO"))
			selectedVM = selectVmOnLoadBalancer(task);
		else
			selectedVM = selectVmOnHost(task);
		
		return selectedVM;
	}
	
	public EdgeVM selectVmOnHost(Task task){
		EdgeVM selectedVM = null;
		
		Location deviceLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(), CloudSim.clock());
		//in our scenasrio, serving wlan ID is equal to the host id
		//because there is only one host in one place
		int relatedHostId=deviceLocation.getServingWlanId();
		List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(relatedHostId);
		
		if(policy.equalsIgnoreCase("RANDOM_FIT")){
			int randomIndex = SimUtils.getRandomNumber(0, vmArray.size()-1);
			double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(randomIndex).getVmType());
			double targetVmCapacity = (double)100 - vmArray.get(randomIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			if(requiredCapacity <= targetVmCapacity)
				selectedVM = vmArray.get(randomIndex);
		}
		else if(policy.equalsIgnoreCase("WORST_FIT")){
			double selectedVmCapacity = 0; //start with min value
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
					selectedVM = vmArray.get(vmIndex);
					selectedVmCapacity = targetVmCapacity;
				}
			}
		}
		else if(policy.equalsIgnoreCase("BEST_FIT")){
			double selectedVmCapacity = 101; //start with max value
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity && targetVmCapacity < selectedVmCapacity){
					selectedVM = vmArray.get(vmIndex);
					selectedVmCapacity = targetVmCapacity;
				}
			}
		}
		else if(policy.equalsIgnoreCase("FIRST_FIT")){
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity){
					selectedVM = vmArray.get(vmIndex);
					break;
				}
			}
		}
		else if(policy.equalsIgnoreCase("NEXT_FIT")){
			int tries = 0;
			while(tries < vmArray.size()){
				lastSelectedVmIndexes[relatedHostId] = (lastSelectedVmIndexes[relatedHostId]+1) % vmArray.size();
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(lastSelectedVmIndexes[relatedHostId]).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(lastSelectedVmIndexes[relatedHostId]).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity){
					selectedVM = vmArray.get(lastSelectedVmIndexes[relatedHostId]);
					break;
				}
				tries++;
			}
		}
		
		return selectedVM;
	}

	public EdgeVM selectVmOnLoadBalancer(Task task){
		EdgeVM selectedVM = null;
		
		if(policy.equalsIgnoreCase("RANDOM_FIT")){
			int randomHostIndex = SimUtils.getRandomNumber(0, numberOfHost-1);
			List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(randomHostIndex);
			int randomIndex = SimUtils.getRandomNumber(0, vmArray.size()-1);
			
			double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(randomIndex).getVmType());
			double targetVmCapacity = (double)100 - vmArray.get(randomIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			if(requiredCapacity <= targetVmCapacity)
				selectedVM = vmArray.get(randomIndex);
		}
		else if(policy.equalsIgnoreCase("WORST_FIT")){
			double selectedVmCapacity = 0; //start with min value
			for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity && targetVmCapacity > selectedVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
				}
			}
		}
		else if(policy.equalsIgnoreCase("BEST_FIT")){
			double selectedVmCapacity = 101; //start with max value
			for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity && targetVmCapacity < selectedVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						selectedVmCapacity = targetVmCapacity;
					}
				}
			}
		}
		else if(policy.equalsIgnoreCase("FIRST_FIT")){
			for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(hostIndex);
				for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity){
						selectedVM = vmArray.get(vmIndex);
						break;
					}
				}
			}
		}
		else if(policy.equalsIgnoreCase("NEXT_FIT")){
			int hostCheckCounter = 0;	
			while(selectedVM == null && hostCheckCounter < numberOfHost){
				int tries = 0;
				lastSelectedHostIndex = (lastSelectedHostIndex+1) % numberOfHost;

				List<EdgeVM> vmArray = SimManager.getInstance().getEdgeServerManager().getVmList(lastSelectedHostIndex);
				while(tries < vmArray.size()){
					lastSelectedVmIndexes[lastSelectedHostIndex] = (lastSelectedVmIndexes[lastSelectedHostIndex]+1) % vmArray.size();
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(lastSelectedVmIndexes[lastSelectedHostIndex]).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(lastSelectedVmIndexes[lastSelectedHostIndex]).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity){
						selectedVM = vmArray.get(lastSelectedVmIndexes[lastSelectedHostIndex]);
						break;
					}
					tries++;
				}

				hostCheckCounter++;
			}
		}
		
		return selectedVM;
	}

	@Override
	public void processEvent(SimEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void shutdownEntity() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startEntity() {
		// TODO Auto-generated method stub
		
	}
}
package edu.boun.edgecloudsim;

import edu.boun.edgecloudsim.core.*;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class Main {
    public static void main(String[] args) {
        //disable console output of cloudsim library
        Log.disable();

        //enable console output and file output of this application
        SimLogger.enablePrintLog();

        int iterationNumber = 1;
        String configFile = "";
        String outputFolder = "";
        String edgeDevicesFile = "";
        String applicationsFile = "";
        String roadNodesFile;
        String outFolderBase = "sim_results";
        if (args.length == 6) {
            configFile = args[0];
            edgeDevicesFile = args[1];
            applicationsFile = args[2];
            roadNodesFile = args[5];
        } else {
            SimLogger.printLine("Simulation setting file, output folder and iteration number are not provided! Using default ones...");
            configFile = "resources/config/default_config.properties";
            applicationsFile = "resources/config/applications.xml";
            edgeDevicesFile = "resources/config/edge_devices.xml";
            roadNodesFile = "resources/config/road_nodes.xml";
        }
        Paths.get(outFolderBase).toFile().mkdirs();

        //load settings from configuration file
        SimSettings SS = SimSettings.getInstance();
        if (!SS.initialize(configFile, edgeDevicesFile, applicationsFile, roadNodesFile)) {
            SimLogger.printLine("cannot initialize simulation settings!");
            System.exit(0);
        }

        if (SS.getFileLoggingEnabled()) {
            SimLogger.enableFileLog();
            SimUtils.cleanOutputFolder(outFolderBase);
        }

        DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Date SimulationStartDate = Calendar.getInstance().getTime();
        String now = df.format(SimulationStartDate);
        SimLogger.printLine("Simulation started at " + now);
        SimLogger.printLine("----------------------------------------------------------------------");
        for (int i = 1; i <= 10; i++) {
            iterationNumber = i;
            outputFolder = outFolderBase + "/ite" + iterationNumber;
            Path path = Paths.get(outputFolder);
            path.toFile().mkdirs();
            for (int j = SS.getMinNumOfMobileDev(); j <= SS.getMaxNumOfMobileDev(); j += SS.getMobileDevCounterSize()) {
                for (int k = 0; k < SS.getSimulationScenarios().length; k++) {
                    for (OrchestratorPolicy orchestratorPolicy : SS.getOrchestratorPolicies()) {

                        String simScenario = SS.getSimulationScenarios()[k];

                        Date ScenarioStartDate = Calendar.getInstance().getTime();
                        now = df.format(ScenarioStartDate);

                        SimLogger.printLine("Scenario started at " + now);
                        SimLogger.printLine("Scenario: " + simScenario + " - Policy: " + orchestratorPolicy + " - #iteration: " + iterationNumber);
                        SimLogger.printLine("Duration: " + SS.getSimulationTime() / 3600 + " hour(s) - Poisson: " + SS.getTaskLookUpTable()[0][2] + " - #devices: " + j);
                        SimLogger.getInstance().simStarted(outputFolder, "SIMRESULT_" + simScenario + "_" + orchestratorPolicy + "_" + j + "DEVICES");

                        try {
                            // First step: Initialize the CloudSim package. It should be called
                            // before creating any entities.
                            int num_user = 2;   // number of grid users
                            Calendar calendar = Calendar.getInstance();
                            boolean trace_flag = false;  // mean trace events

                            // Initialize the CloudSim library
                            CloudSim.init(num_user, calendar, trace_flag, 0.01);

                            // Generate EdgeCloudsim Scenario Factory
                            ScenarioFactory sampleFactory = new VehicularScenarioFactory(j, SS.getSimulationTime(), simScenario, orchestratorPolicy);

                            // Generate EdgeCloudSim Simulation Manager
                            SimManager manager = new SimManager(sampleFactory, j, simScenario, orchestratorPolicy);

                            // Start simulation
                            manager.startSimulation();
                        } catch (Exception e) {
                            SimLogger.printLine("The simulation has been terminated due to an unexpected error");
                            e.printStackTrace();
                            System.exit(0);
                        }

                        Date ScenarioEndDate = Calendar.getInstance().getTime();
                        now = df.format(ScenarioEndDate);
                        SimLogger.printLine("Scenario finished at " + now + ". It took " + SimUtils.getTimeDifference(ScenarioStartDate, ScenarioEndDate));
                        SimLogger.printLine("----------------------------------------------------------------------");

                    }//End of orchestrators loop
                }//End of scenarios loop
            }//End of mobile devices loop
        }//End of iterations loop

        Date SimulationEndDate = Calendar.getInstance().getTime();
        now = df.format(SimulationEndDate);
        SimLogger.printLine("Simulation finished at " + now + ". It took " + SimUtils.getTimeDifference(SimulationStartDate, SimulationEndDate));
    }
}

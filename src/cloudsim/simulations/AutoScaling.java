package cloudsim.simulations;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

/**
 * A simple example showing how to implement auto scaling in CloudSim.
 * This example creates a datacenter with VMs and cloudlets, and implements
 * a basic auto scaling policy based on CPU utilization thresholds.
 */
public class AutoScaling {

    /** The cloudlet list. */
    private static List<Cloudlet> cloudletList;

    /** The vm list. */
    private static List<Vm> vmList;

    /** VM ID counter to ensure unique VM IDs */
    private static int vmIdCounter = 0;

    /** The datacenter. */
    private static Datacenter datacenter0;

    /**
     * Creates main() to run this example.
     */
    public static void main(String[] args) {
        Log.printLine("Starting AutoScaling CloudSim Example...");

        try {
            // First step: Initialize the CloudSim package
            int num_user = 1;   // number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;  // mean trace events

            // Initialize the CloudSim library
            CloudSim.init(num_user, calendar, trace_flag);

            // Second step: Create Datacenters
            datacenter0 = createDatacenter("Datacenter_0");

            // Third step: Create Broker
            DatacenterBroker broker = createBroker();
            int brokerId = broker.getId();

            // Initialize VM list before creating VMs
            vmList = new ArrayList<>();
            
            // Fourth step: Create VMs and Cloudlets
            vmList = createVMs(brokerId);
            cloudletList = createCloudlets(brokerId, 10);

            // Submit vm list to the broker
            broker.submitVmList(vmList);

            // Submit cloudlet list to the broker
            broker.submitCloudletList(cloudletList);

            // Fifth step: Starts the simulation
            CloudSim.startSimulation();

            // Final step: Auto-scaling based on CPU utilization
            implementAutoScaling(broker, brokerId);

            // Print results when simulation is over
            List<Cloudlet> finalCloudletList = broker.getCloudletReceivedList();
            printCloudletList(finalCloudletList);

            CloudSim.stopSimulation();

            Log.printLine("Auto Scaling CloudSim Example finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("The simulation has been terminated due to an unexpected error");
        }
    }

    /**
     * Implements auto scaling based on CPU utilization thresholds.
     * This method checks the CPU utilization of each VM and scales up or down
     * based on predefined thresholds.
     */
    private static void implementAutoScaling(DatacenterBroker broker, int brokerId) {
        Log.printLine("\n\n========== IMPLEMENTING AUTO SCALING ==========");
        
        // Define scaling thresholds
        double upperThreshold = 0.8; // 80% CPU utilization
        double lowerThreshold = 0.2; // 20% CPU utilization
        
        // Create a list to store VMs to be removed (to avoid ConcurrentModificationException)
        List<Vm> vmsToRemove = new ArrayList<>();
        
        // Monitor each VM's CPU utilization
        for (Vm vm : vmList) {
            // In a real simulation, you would get actual CPU utilization
            // Here we simulate by generating a random utilization
            double cpuUtilization = Math.random(); // Simulated CPU utilization
            
            Log.printLine("VM #" + vm.getId() + " CPU Utilization: " + 
                          new DecimalFormat("##.##").format(cpuUtilization * 100) + "%");
            
            if (cpuUtilization > upperThreshold) {
                // Scale up: Create a new VM
                Log.printLine("VM #" + vm.getId() + " exceeds upper threshold. Scaling up...");
                
                // Create a new VM with the same configuration
                // Cast double to int for the MIPS value
                Vm newVm = createVM(brokerId, (int)vm.getMips(), vm.getNumberOfPes());
                vmList.add(newVm);
                
                // Submit the new VM to the broker
                List<Vm> newVmList = new ArrayList<>();
                newVmList.add(newVm);
                broker.submitVmList(newVmList);
                
                Log.printLine("New VM #" + newVm.getId() + " created and submitted.");
            } 
            else if (cpuUtilization < lowerThreshold && vmList.size() > 1) {
                // Scale down: Remove a VM (but keep at least one)
                Log.printLine("VM #" + vm.getId() + " below lower threshold. Scaling down...");
                
                // Mark this VM for removal
                vmsToRemove.add(vm);
                
                Log.printLine("VM #" + vm.getId() + " marked for removal.");
                break; // Only remove one VM per scaling decision
            }
        }
        
        // Remove the marked VMs
        for (Vm vm : vmsToRemove) {
            vmList.remove(vm);
            // In actual CloudSim, you would use:
            // broker.destroyVm(vm.getId());
        }
        
        Log.printLine("Total VMs after scaling: " + vmList.size());
        Log.printLine("==============================================\n");
    }

    /**
     * Creates the datacenter.
     */
    private static Datacenter createDatacenter(String name) {
        // Create a list to store hosts
        List<Host> hostList = new ArrayList<>();

        // Host characteristics
        int mips = 1000;
        int hostId = 0;
        int ram = 16384; // 16 GB
        long storage = 1000000; // 1 TB
        int bw = 10000; // 10 Gbps

        // Create PEs
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            peList.add(new Pe(i, new PeProvisionerSimple(mips)));
        }

        // Create Hosts with specified configurations
        hostList.add(
            new Host(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerSimple(bw),
                storage,
                peList,
                new VmSchedulerTimeShared(peList)
            )
        );

        // Create a DatacenterCharacteristics object
        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);

        Datacenter datacenter = null;
        try {
            datacenter = new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), 
                                       new LinkedList<Storage>(), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return datacenter;
    }

    /**
     * Creates the broker.
     */
    private static DatacenterBroker createBroker() {
        DatacenterBroker broker = null;
        try {
            broker = new DatacenterBroker("Broker");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return broker;
    }

    /**
     * Creates a VM with given specifications.
     */
    private static Vm createVM(int userId, int mips, int numPes) {
        // VM description
        int vmid = vmIdCounter++;  // Use counter to ensure unique VM IDs
        long size = 10000; // image size (MB)
        int ram = 512; // VM memory (MB)
        long bw = 1000;
        
        // Create VM
        Vm vm = new Vm(vmid, userId, mips, numPes, ram, bw, size, "Xen", 
                      new CloudletSchedulerTimeShared());
        return vm;
    }

    /**
     * Creates a list of VMs.
     */
    private static List<Vm> createVMs(int userId) {
        // Creates a container to store VMs
        List<Vm> vms = new ArrayList<>();

        // Create two VMs to start with
        vms.add(createVM(userId, 1000, 2));
        vms.add(createVM(userId, 1000, 2));

        return vms;
    }

    /**
     * Creates cloudlet list.
     */
    private static List<Cloudlet> createCloudlets(int userId, int numCloudlets) {
        // Creates a container to store Cloudlets
        List<Cloudlet> list = new ArrayList<>();

        // cloudlet parameters
        long length = 10000;
        long fileSize = 300;
        long outputSize = 300;
        int pesNumber = 1;
        UtilizationModel utilizationModel = new UtilizationModelFull();

        for (int i = 0; i < numCloudlets; i++) {
            Cloudlet cloudlet = new Cloudlet(i, length, pesNumber, fileSize, 
                                           outputSize, utilizationModel, utilizationModel, 
                                           utilizationModel);
            cloudlet.setUserId(userId);
            list.add(cloudlet);
        }

        return list;
    }

    /**
     * Prints the Cloudlet objects.
     */
    private static void printCloudletList(List<Cloudlet> list) {
        int size = list.size();
        Cloudlet cloudlet;

        String indent = "    ";
        Log.printLine();
        Log.printLine("========== OUTPUT ==========");
        Log.printLine("Cloudlet ID" + indent + "STATUS" + indent +
                "Data center ID" + indent + "VM ID" + indent + "Time" + indent + "Start Time" + indent + "Finish Time");

        DecimalFormat dft = new DecimalFormat("###.##");
        for (int i = 0; i < size; i++) {
            cloudlet = list.get(i);
            Log.print(indent + cloudlet.getCloudletId() + indent + indent);

            if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
                Log.print("SUCCESS");

                Log.printLine(indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId() +
                        indent + indent + dft.format(cloudlet.getActualCPUTime()) + indent + indent + dft.format(cloudlet.getExecStartTime()) +
                        indent + indent + dft.format(cloudlet.getFinishTime()));
            }
        }
    }
}
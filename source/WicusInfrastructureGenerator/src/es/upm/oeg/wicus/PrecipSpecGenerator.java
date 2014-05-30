package es.upm.oeg.wicus;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

import es.upm.oeg.wicus.queries.QueryExecute;
import es.upm.oeg.wicus.queries.QueryStrings;
import es.upm.oeg.wicus.utils.Digraph;

public class PrecipSpecGenerator 
{
	private static String INT_BASE_ID ="inst";
	

	private static OntModel wfiModel;
	private static OntModel swcModel;
	private static OntModel svaModel;
	

	private static String DEFAULT_INSTANCE_TYPE = "m1.large";


	private static String PAR_NOT_FOUND_ERR_MSG = "Paramater from Requirement not found on Plan: ";



	private static String PRECIP_USER ="root";
	private static String WICUS_USER ="cloud-user";


	private static String DEST_COMMAND_PATH = "/home/cloud-user/";   
	
	public PrecipSpecGenerator(OntModel wfiM, OntModel swcM, OntModel svaM)
	{
		this.wfiModel = wfiM;
		this.swcModel = swcM;
		this.svaModel = svaM;
	}
	
	public String mergeMaxSVAandGenerateInfConfigurationPrecip(Map<String, String> reqMaxSvaList,Map<String, Digraph<String>> deployReqDg, ArrayList<String> providersList) 
	{
		
		String conf = "";
		
		HashMap<String, HashMap<String, ArrayList<String>>> mergedSvas = mergeMaxSVA(reqMaxSvaList);
		
		conf = generatePrecipConf(mergedSvas, deployReqDg, providersList);
		
		return conf;
		
	}
	
	public String mergeMaxSVAandGenerateInfConfigurationPropagatedPrecip(Map<String, String> wfMaxSvaList,Map<String, LinkedHashMap<String, Digraph<String>>> deployWfDg,Map<String, Digraph<String>> deployReqDg, ArrayList<String> providersList) 
	{
		String conf = "";
		
		//merge SVAs
		//TODO: get map<SVA,map<SVA_ID,linkedhashset<REQ>>> (or this?)
		HashMap<String, HashMap<String, LinkedHashSet<String>>> mergedPropagatedSvas = mergeWfMaxSVA(wfMaxSvaList,deployWfDg);
		
		//TODO: modify this method or use the same as above
		conf = generatePrecipPropagatedConf(mergedPropagatedSvas, deployWfDg,deployReqDg, providersList);	
		
		return conf;
	}
	


	private String printMergedPropagatedSvas(HashMap<String, HashMap<String, LinkedHashSet<String>>> mergedPropagatedSvas, Map<String, Digraph<String>> deployReqDg) {
		String res="";
		Iterator<Entry<String, HashMap<String, LinkedHashSet<String>>>> it = mergedPropagatedSvas.entrySet().iterator();
	    while (it.hasNext()) 
	    {
	    	Entry<String, HashMap<String, LinkedHashSet<String>>> pairs = it.next();
	    	String string1 = pairs.getKey().toString();
	    	Map<String, LinkedHashSet<String>> map2 = pairs.getValue();
	    	

	    	res+="\n";
	    	res+="* Key: " + string1 + "\n";
	    	res+="* List:" + "\n";
	    	
	    	Iterator<Entry<String, LinkedHashSet<String>>> it2 = map2.entrySet().iterator();
		    while (it2.hasNext()) 
		    {
		    	Entry<String, LinkedHashSet<String>> pairs2 = it2.next();
		    	String string2 = pairs2.getKey().toString();
		    	HashSet<String> list = pairs2.getValue();
		    	res+="*\tKEY:" + string2.toString() + "\n";
		    	res+="*\tVALUES:\n";
		    	for(String s : list)
		    	{
		    		res+="*\t-" + s.toString() + "\n";
//		    		res+="*\t\t" +deployReqDg.get(s) + "\n";
		    	}   		
		    }
		
	    	res+="* ----------------------------------------------------\n";
	    	
	    }		
		return res;
	}

	private String generatePrecipPropagatedConf(HashMap<String, HashMap<String, LinkedHashSet<String>>> mergedPropagatedSvas,	Map<String, LinkedHashMap<String, Digraph<String>>> deployWfDg, Map<String, Digraph<String>> deployReqDg, ArrayList<String> providersList) 
	{
		String conf = "#!/usr/bin/python\n" +
				"\n" +
				"import logging\n" +
				"import os\n" +
				"import time\n" +
				"from pprint import pprint\n" +
				"\n" +
				"from precip import *\n" +
				"\n" +
				"exp = None\n" +
				"\n" +
				"# Use try/except liberally in your experiments - the api is set up to\n" +
				"# raise ExperimentException on most errors\n" +
				"try:\n" +
				"\n" +
				"    # Create a new OpenStack based experiment. In this case we pick\n" +
				"    # up endpoints and access/secret cloud keys from the environment\n" +
				"    # as exposing those is the common setup on FutureGrid\n";
				
				/*
				"    exp = OpenStackExperiment(\n" +
				"            os.environ['OPENSTACK_URL'],\n" +
				"            os.environ['OPENSTACK_ACCESS_KEY'],\n" +
				"            os.environ['OPENSTACK_SECRET_KEY'])\n\n\n";
				*/
		
		Iterator<Entry<String, HashMap<String, LinkedHashSet<String>>>> svaIt = mergedPropagatedSvas.entrySet().iterator();
		while(svaIt.hasNext())
		{
			Entry<String, HashMap<String, LinkedHashSet<String>>> svaIdPair = svaIt.next();
			String sva = svaIdPair.getKey();

			//System.out.println("SVA="+sva);
			
			
			HashMap<String, LinkedHashSet<String>> svaIdMap = svaIdPair.getValue();
			Iterator<Entry<String, LinkedHashSet<String>>> svaIdIt = svaIdMap.entrySet().iterator();
			int idCounter = 0;
			
			while(svaIdIt.hasNext())
			{
				Entry<String, LinkedHashSet<String>> wfPair = svaIdIt.next();
				
				String instId = INT_BASE_ID + idCounter;
				idCounter++;
				
				
				//for each svaID we need to generate an SVA
				conf += getSvaPrecipInfo(sva,instId,providersList);
				
				conf+="    # Wait for all instances to boot and become accessible. The provision\n" +
					  "    # method only starts the provisioning, and can be used to start a large\n" +
					  "    # number of instances at the same time. The wait method provides a\n" +
					  "    # barrier to when it is safe to start the actual experiment.\n";

				conf+="    exp.wait()\n\n";
				

				conf += generateUserSetup(instId,WICUS_USER);
				
				//we traverse all the WFs
				HashSet<String> reqList = wfPair.getValue();
				for(String req : reqList)
				{
			    	conf += getReqPrecipInfo(req,deployReqDg,instId);
				}

				
				try {
					conf += readWfExecFile("files/wfexec.txt",instId,WICUS_USER) + "\n";
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		
		
		conf+="except ExperimentException as e:\n"+
				"    # This is the default exception for most errors in the api\n"+
				"    print \"ERROR: %s\" % e\n\n"+

				"finally:\n"+
				"    # Be sure to always deprovision the instances we have started. Putting\n"+
				"    # the deprovision call under finally: make the deprovisioning happening\n"+
				"    # even in the case of failure.\n"+
				"    if exp is not None:\n"+
				"        exp.deprovision()\n";
		
		return conf;
	}

	private static String generateUserSetup(String instId, String user) 
	{
		String res = "    # copy user_setup.sh to the instance\n"+
				"    exp.put([\"%INSTID%\"], \"user_setup.sh\", \"/root/user_setup.sh\",user=\"" + PRECIP_USER + "\")\n\n"+
				"    # granting execution for user_setup.sh\n"+
				"    exp.run([\"%INSTID%\"], \"chmod 755 /root/user_setup.sh\", user=\"" + PRECIP_USER + "\")\n\n"+
				"    # executing the user_setup.sh script\n"+
				"    exp.run([\"%INSTID%\"], \"/root/user_setup.sh -USER_NAME %USER% -SSH_KEYS_PATH /root\", user=\"" + PRECIP_USER + "\")\n\n";

	    res = res.replace("%INSTID%", instId);
	    res = res.replace("%USER%", user);
		
		return res;
	}

	private static String getExperimentCreationCommand(String provider)
	{
		String res = "";
		if(provider.equals("http://purl.org/net/wicus-sva/resource/InfrastructureProvider/FutureGrid_CLOUD_PROV"))
		{
			res = 	"    exp = OpenStackExperiment(\n" +
					"            os.environ['OPENSTACK_URL'],\n" +
					"            os.environ['OPENSTACK_ACCESS_KEY'],\n" +
					"            os.environ['OPENSTACK_SECRET_KEY'])\n\n\n";
			
		}
		if(provider.equals("http://purl.org/net/wicus-sva/resource/InfrastructureProvider/AmazonWebServices_CLOUD_PROV"))
		{
			res = 	"    exp = EC2Experiment(\n" +
					"            os.environ['AMAZON_EC2_REGION'],\n" +
					"            os.environ['AMAZON_EC2_URL'],\n" +
					"            os.environ['AMAZON_EC2_ACCESS_KEY'],\n" +
					"            os.environ['AMAZON_EC2_SECRET_KEY'])\n\n\n";
		}
		
		return res;
	}

	//this method merges requirements into SVAs that are equal if those requirements are compatible.
	//it returns a map<SVA,map<SVA_ID,hashset<REQ>>>
	//SVA is the base appliance, where SVA_ID is an instantiation of the appliance for a set of requirementes
	//this is like this because it could be the case that 2 WF are associated to the same base SVA,
	//but they are incompatible, so we need two instantiate 2 different SVA for them
	private HashMap<String, HashMap<String, LinkedHashSet<String>>> mergeWfMaxSVA(Map<String, String> wfMaxSvaList, Map<String, LinkedHashMap<String, Digraph<String>>> deployWfDg) 
	{
				
		HashMap<String,HashMap<String,LinkedHashSet<String>>> res = new HashMap<String,HashMap<String,LinkedHashSet<String>>>();
		
		Iterator<Entry<String, String>> wfIt = wfMaxSvaList.entrySet().iterator();
		while(wfIt.hasNext())
		{
			Entry<String, String> pair = wfIt.next();
			String wf = pair.getKey();
			String sva = pair.getValue();
			
			if(res.containsKey(sva))
			{
				//we iterate all the svaIds for an SVA
				HashMap<String, LinkedHashSet<String>> svaMap = res.get(sva);
				Iterator<Entry<String, LinkedHashSet<String>>> svaIter = svaMap.entrySet().iterator();
				while (svaIter.hasNext())
				{
					Entry<String, LinkedHashSet<String>> svaIdPair = svaIter.next();
					String svaId = svaIdPair.getKey();
					HashSet<String> svaIdReqs = svaIdPair.getValue();
					
					if(checkWfCompatibility(wf,svaIdReqs,deployWfDg))
					{
						//if the wfs is compatible with the others
						//we add its requirements (no duplicates) to the list of the sva
						Set<String> l = deployWfDg.get(wf).keySet();
						System.out.println("--------ADDING:" + l);
						res.get(sva).get(svaId).addAll(l);
					}
					else
					{
						HashMap<String, LinkedHashSet<String>> map = res.get(sva);
						
						//we generate a new identifier for the sva
						int size = map.entrySet().size();
						String newSvaId = sva+size;
						map.put(newSvaId, new LinkedHashSet<String>());

						//we add its requirements (no duplicates) to the list of the sva
						Set<String> l = deployWfDg.get(wf).keySet();
						map.get(newSvaId).addAll(l);
					}
					
				}
				
			}
			else
			{
				res.put(sva, new HashMap<String,LinkedHashSet<String>>());
				HashMap<String, LinkedHashSet<String>> map = res.get(sva);
				
				//we generate a new identifier for the sva
				int size = map.entrySet().size();
				String newSvaId = sva+size;
				map.put(newSvaId, new LinkedHashSet<String>());
				
				//we add its requirements (no duplicates) to the list of the sva
				Set<String> l = deployWfDg.get(wf).keySet();

				System.out.println("--------ADDING:" + l);
				map.get(newSvaId).addAll(l);
				
				//at this point we have
				//<SVA,<SVA0,<REQS>>>
				
			}
			
		}
		
		return res;
	}

	private static String generatePrecipConf( HashMap<String, HashMap<String, ArrayList<String>>> mergedSvas, Map<String, Digraph<String>> deployReqDg, ArrayList<String> providersList) 
	{
		String conf = "#!/usr/bin/python\n" +
				"\n" +
				"import logging\n" +
				"import os\n" +
				"import time\n" +
				"from pprint import pprint\n" +
				"\n" +
				"from precip import *\n" +
				"\n" +
				"exp = None\n" +
				"\n" +
				"# Use try/except liberally in your experiments - the api is set up to\n" +
				"# raise ExperimentException on most errors\n" +
				"try:\n" +
				"\n" +
				"    # Create a new OpenStack based experiment. In this case we pick\n" +
				"    # up endpoints and access/secret cloud keys from the environment\n" +
				"    # as exposing those is the common setup on FutureGrid\n" +
				"    exp = OpenStackExperiment(\n" +
				"            os.environ['OPENSTACK_URL'],\n" +
				"            os.environ['OPENSTACK_ACCESS_KEY'],\n" +
				"            os.environ['OPENSTACK_SECRET_KEY'])\n\n\n";
		
		
		Iterator<Entry<String, HashMap<String, ArrayList<String>>>> svaIt = mergedSvas.entrySet().iterator();
		while(svaIt.hasNext())
		{
			Entry<String, HashMap<String, ArrayList<String>>> svaIdPair = svaIt.next();
			String sva = svaIdPair.getKey();
			
			//System.out.println("SVA="+sva);
			
			
			HashMap<String, ArrayList<String>> svaIdMap = svaIdPair.getValue();
			Iterator<Entry<String, ArrayList<String>>> svaIdIt = svaIdMap.entrySet().iterator();
			int idCounter = 0;
			while(svaIdIt.hasNext())
			{
				Entry<String, ArrayList<String>> reqsPair = svaIdIt.next();
				
				String instId = INT_BASE_ID +idCounter;
				idCounter++;
				
				//for each svaID we need to generate an SVA
				conf += getSvaPrecipInfo(sva,instId, providersList);
				
				
				conf+="    # Wait for all instances to boot and become accessible. The provision\n" +
					  "    # method only starts the provisioning, and can be used to start a large\n" +
					  "    # number of instances at the same time. The wait method provides a\n" +
					  "    # barrier to when it is safe to start the actual experiment.\n";

				conf+="    exp.wait()\n\n";
				

				conf += generateUserSetup(instId,WICUS_USER);
				
				ArrayList<String> reqList = reqsPair.getValue();
				for(String req : reqList)
				{
					//System.out.println("----"+r);
			    	conf += getReqPrecipInfo(req,deployReqDg,instId);
				}
				try {
					conf += readWfExecFile("files/wfexec.txt",instId,WICUS_USER) + "\n";
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		
		conf+="except ExperimentException as e:\n"+
				"    # This is the default exception for most errors in the api\n"+
				"    print \"ERROR: %s\" % e\n\n"+

				"finally:\n"+
				"    # Be sure to always deprovision the instances we have started. Putting\n"+
				"    # the deprovision call under finally: make the deprovisioning happening\n"+
				"    # even in the case of failure.\n"+
				"    if exp is not None:\n"+
				"        exp.deprovision()\n";
		
		return conf;
	}

	private static String getSvaPrecipInfo(String sva, String instId, ArrayList<String> providersList) 
	{
		String conf ="";
		
		
		//get the image appliance that best supports the SVA
    	ResultSet getSvaInfoQR = QueryExecute.execute(svaModel, QueryStrings.getSvaInfoQS, "sva_uri", sva);
    	QuerySolution iapp = getSvaImageAppliance(getSvaInfoQR, providersList);

    	//String imageApp  = iapp.get("?iapp").toString();
    	//String vmImage  = iapp.get("?vmimg").toString();
    	String vmAppProvUri  = iapp.get("?prov").toString();
    	
    	//String vmAppProv = cv.getCloudProviderName(vmAppProvUri);
    	String vmImageId  = iapp.get("?vmid").toString();
    	
    	

		conf+= getExperimentCreationCommand(vmAppProvUri);
		
		String instanceType = getInstanceType(vmAppProvUri);
    	conf+= "    exp.provision(\""+vmImageId+"\", instance_type=\""+instanceType+"\",\n";
    	conf+= "                    tags=[\""+instId+"\"], count=1)\n\n";
    	
    	//conf+="//Image appliance=" + imageApp + "\n";
    	//conf+="//VmImage=" + vmImage + "\n";
    	//conf+="VM Id=" + vmImageId + "\n";
    	
    	
    	return conf;
	}
	
	private static String getInstanceType(String provider) 
	{
		
		String res = "";
		if(provider.equals("http://purl.org/net/wicus-sva/resource/InfrastructureProvider/FutureGrid_CLOUD_PROV"))
		{
			res = "m1.large";
			
		}
		if(provider.equals("http://purl.org/net/wicus-sva/resource/InfrastructureProvider/AmazonWebServices_CLOUD_PROV"))
		{
			res = "t1.micro";
		}
		// TODO Auto-generated method stub
		return res;
	}

	//returns the image appliance (along with VM image and VM image id) that best suits the SVA
	//TODO up to now it only returns the first entry the providers list
	//we have to add the logic for selecting the best one
	private static QuerySolution getSvaImageAppliance(ResultSet qr, ArrayList<String> providersList) 
	{

	     System.out.println("*********** providersList **********");
	     System.out.println("* " + providersList.toString());
	     System.out.println("***************************************\n");
	     
	     while(qr.hasNext())
	     {
	    	 QuerySolution qs = qr.next();
	     	 String vmAppProvUri  = qs.get("?prov").toString();
	     	 
	     	 if(providersList.contains(vmAppProvUri))
	     		 return qs;
	     } 
	     return null;
	}
	
	private static String getReqPrecipInfo(String req, Map<String, Digraph<String>> deployReqDg, String instId) 
	{
		String conf = "";
		//retrieve the Sw Components from SW catalog of each stack of the DGraph and add it to the conf.
    	Digraph<String> dg = deployReqDg.get(req);
    	
	     
	     System.out.println("*********** DG_TOP_SORT **********");
	     System.out.println("* " + dg.topSort());
	     System.out.println("***************************************\n");
	     

	     System.out.println("*********** DG_REVERSE_SORT **********");
	     System.out.println("* " + dg.reverseTopSort());
	     System.out.println("***************************************\n");
	     
	     
    	//iterate the list of stacks and get the SW comp for each one
    	Iterator<String> stackIt = dg.reverseTopSort().iterator();
    	while(stackIt.hasNext())
    	{
			
    		String stackUri = stackIt.next();
    		ResultSet getSwCompSwStackQR = QueryExecute.execute(swcModel, QueryStrings.getSwCompSwStackQS, "stack_uri", stackUri);
    		
			conf += "    # [STACK] Deployment of "+stackUri+" stack\n\n";

    		
    		//for each SW comp we get its dep. plan and its configuration info.
    		while(getSwCompSwStackQR.hasNext())
    		{
    			QuerySolution qs = getSwCompSwStackQR.next();
    			String swc = qs.get("?swc").toString();
    			

    			conf += "    # [COMPONENT] Deployment of "+swc+" component\n\n";
    			
    			//get SWC Conf Info, and store on a HashMap<PARNAME,PARVALUE> 
    			//representing the value for each par as expressed in the reqs
    			HashMap<String,String> reqParNameValues = new HashMap<String,String>();
    			ResultSet getConfParSwCompQR = QueryExecute.execute(wfiModel, QueryStrings.getConfParSwCompQS, "swc_uri", swc);
    			while(getConfParSwCompQR.hasNext())
	    		{
    				QuerySolution qsp = getConfParSwCompQR.next();
	    			//String confPar = qsp.get("?confpar").toString();
	    			
	    			String parName = qsp.get("?parname").asLiteral().getString();
	    			String parValue = qsp.get("?parvalue").asLiteral().getString();
	    			reqParNameValues.put(parName, parValue);
	    			
	    		}
    			
    			ResultSet getDepPlanSwcCompQR = QueryExecute.execute(swcModel, QueryStrings.getDepPlanSwcCompQS, "swc_uri", swc);
    			while(getDepPlanSwcCompQR.hasNext())
	    		{
    				QuerySolution qsp = getDepPlanSwcCompQR.next();
	    			String depPlan = qsp.get("?depplan").toString();
    				    				
	    			//Get Dep Steps of a Dep Plan ()
        			ResultSet getDepStepsDepPlanQR = QueryExecute.execute(swcModel, QueryStrings.getDepStepsDepPlanQS, "dp_uri", depPlan);
        			
        			//Get Dep Steps linked by nextStep
        			ResultSet getNextDepStepsDePlanQR = QueryExecute.execute(swcModel, QueryStrings.getNextDepStepsDePlanQS, "dp_uri", depPlan);
        			

    				//TODO: add step order
        			//here we have all the steps, but we have to sort them
        			
        			//get all the steps and add them to a graph
        			Digraph<String> nextStepsGraph = new Digraph<String>();
        			
        			while(getDepStepsDepPlanQR.hasNext())
        			{
        				QuerySolution qsDs = getDepStepsDepPlanQR.next();
        				String depStep = qsDs.get("?depstep").toString();
        				nextStepsGraph.add(depStep);
        			}
        			

        			//link them
        			while(getNextDepStepsDePlanQR.hasNext())
        			{
        				QuerySolution qsDs = getNextDepStepsDePlanQR.next();
        				String depStep1 = qsDs.get("?step1").toString();
        				String depStep2 = qsDs.get("?step2").toString();
        				nextStepsGraph.add(depStep1, depStep2);
        			}
        			
        			
        			//sort them
        			List<String> nextStepsList = nextStepsGraph.topSort();

        		     System.out.println("*********** nextStepsList **********");
        		     System.out.println("* " + nextStepsList);
        		     System.out.println("***************************************\n");
        			

//        			while(getDepStepsDepPlanQR.hasNext())
            		for(String depStep : nextStepsList)
    	    		{
//        				QuerySolution qsDs = getDepStepsDepPlanQR.next();
//        				String depStep = qsDs.get("?depstep").toString();
        				
        				//Get Script of and conf info of a Dep Step
            			ResultSet getScriptConfInfoDepStepQR = QueryExecute.execute(swcModel, QueryStrings.getScriptConfInfoDepStepQS, "ds_uri", depStep);
            			while(getScriptConfInfoDepStepQR.hasNext())
        	    		{
            				
            				QuerySolution qsSCI= getScriptConfInfoDepStepQR.next();
            				String script = qsSCI.get("?script").asLiteral().getString();
            				String confInfo = qsSCI.get("?conf").toString();
            				
            				String argsString = "";

                			
            				//Get Conf Par of a given conf info
                			ResultSet getConfParConfInfoQR = QueryExecute.execute(swcModel, QueryStrings.getConfParConfInfoQS, "ci_uri", confInfo);
                			while(getConfParConfInfoQR.hasNext())
            	    		{
                				QuerySolution qspCP = getConfParConfInfoQR.next();
            	    			String confPar = qspCP.get("?confpar").toString();

            	    			//get the literal values of a given conf par 
            	    			ResultSet getParsConfParQR = QueryExecute.execute(swcModel, QueryStrings.getParsConfParQS, "cp_uri", confPar);
                    			while(getParsConfParQR.hasNext())
                	    		{
                    				QuerySolution qspPar = getParsConfParQR.next();
	            	    			String parName = qspPar.get("?parname").asLiteral().getString();
	            	    			String parValue = qspPar.get("?parvalue").asLiteral().getString(); 

	            	    			
	            	    			//we get the value of the parName expressed in the requirements
	            	    			//of not found, we assume that the default value expressed in the
	            	    			//SWC catalogue must be used
	            	    			if(reqParNameValues.containsKey(parName))
	            	    			{
	            	    				parValue = reqParNameValues.get(parName);
	            	    			}
	            	    			else
	            	    			{
	            	    				System.err.println(PAR_NOT_FOUND_ERR_MSG + parName + " of " + parValue);
	            	    				System.err.println(stackUri + "|" + script +  "|" + script + " from: " + reqParNameValues);
	            	    			}	
	            	    			
	            	    			//add the parameter (either default or specific value) to the args line
            	    				argsString += " " + parName + " " + parValue;
                	    		}  

            	    		}
                			
                			//now we have the SCRIPT and the args string

                			conf += "    # [STEP] Excution of "+depStep+" step\n\n";
                			
                			
                			//transfer the script
                			conf += "    # copy "+script+" to the instance\n";
                			//conf += "    print \"copy "+script+" to the instance\"\n";
                			conf += "    exp.put([\""+instId+"\"], \""+script+"\", \""+DEST_COMMAND_PATH+script+"\",user=\""+PRECIP_USER+"\")\n\n";

                			
                			//ensure execution permission for the user
                			conf += "    # granting execution for "+script+"\n";
                			//conf += "    print \"granting execution for "+script+"\"\n";
                			conf += "    exp.run([\""+instId+"\"], \"chmod 755 "+DEST_COMMAND_PATH+script+"\", user=\""+PRECIP_USER+"\")\n\n";

                			
                			//execute the string with the parameters
                			conf += "    # executing the "+script+" script\n";
                			//conf += "    print \"executing the "+script+" script\"\n";
                			conf += "    exp.run([\""+instId+"\"], \""+DEST_COMMAND_PATH+script+argsString+" \", user=\""+PRECIP_USER+"\")\n\n";
                			
        	    		}
        				
    	    		}
	    			
	    		}
    			
    		}
    	}
		return conf;
	}

	//this method merges requirements into SVAs that are equal if those requirements are compatible.
	//it returns a map<SVA,map<SVA_ID,list<REQ>>>
	//SVA is the base appliance, where SVA_ID is an instantiation of the appliance for a set of requirementes
	//this is like this because it could be the case that 2 REQS are associated to the same base SVA,
	//but they are incompatible, so we need two instantiate 2 different SVA for them
	private static HashMap<String, HashMap<String, ArrayList<String>>> mergeMaxSVA(Map<String, String> reqMaxSvaList) 
	{
		
		HashMap<String,HashMap<String,ArrayList<String>>> res = new HashMap<String,HashMap<String,ArrayList<String>>>();
		
		Iterator<Entry<String, String>> reqIt = reqMaxSvaList.entrySet().iterator();
		while(reqIt.hasNext())
		{
			Entry<String, String> pair = reqIt.next();
			String req = pair.getKey();
			String sva = pair.getValue();
			
			if(res.containsKey(sva))
			{
				//we iterate all the svaIds for an SVA
				HashMap<String, ArrayList<String>> svaMap = res.get(sva);
				Iterator<Entry<String, ArrayList<String>>> svaIter = svaMap.entrySet().iterator();
				while (svaIter.hasNext())
				{
					Entry<String, ArrayList<String>> svaIdPair = svaIter.next();
					String svaId = svaIdPair.getKey();
					ArrayList<String> svaIdReqs = svaIdPair.getValue();
					
					if(checkReqsCompatibility(req,svaIdReqs))
					{
						//if the req is compatible with the others
						//we add it to the list of the sva
						res.get(sva).get(svaId).add(req);
					}
					else
					{
						HashMap<String, ArrayList<String>> map = res.get(sva);
						
						//we generate a new identifier for the sva
						int size = map.entrySet().size();
						String newSvaId = sva+size;
						map.put(newSvaId, new ArrayList<String>());
						map.get(newSvaId).add(req);
					}
					
				}
				
			}
			else
			{
				res.put(sva, new HashMap<String,ArrayList<String>>());
				HashMap<String, ArrayList<String>> map = res.get(sva);
				
				//we generate a new identifier for the sva
				int size = map.entrySet().size();
				String newSvaId = sva+size;
				map.put(newSvaId, new ArrayList<String>());
				map.get(newSvaId).add(req);
				
				//at this point we have
				//<SVA,<SVA0,<REQ>>>
				
			}
			
		}
		
		return res;
		
	}

	//This function checks whether a requirement req is compatible with all the requiments
	//already merged in the SVA (svaReqList)
	//TODO add the logic for checking compatibility
	//up to know we are assuming that unless the user says that 2 requirements are INCOMPATIBLE
	//they are always COMPATIBLE 
	private static boolean checkReqsCompatibility(String req, ArrayList<String> svaResList) 
	{
		boolean res = true;
		return res;
	}
	

	//This function checks whether the requirements of a wf workflow wf is compatible with all the requiments
	//already merged in the SVA (svaReqList)
	//TODO add the logic for checking compatibility
	//up to know we are assuming that unless the user says that 2 requirements are INCOMPATIBLE
	//they are always COMPATIBLE 
	//TODO: add a parameter for getting the WF requirements
	private static boolean checkWfCompatibility(String wf, HashSet<String> svaIdReqs, Map<String, LinkedHashMap<String, Digraph<String>>> deployWfDg) 
	{
		boolean res = true;
		return res;
	}

	
	private static String readWfExecFile( String file, String instId, String user) throws IOException {
	    BufferedReader reader = new BufferedReader( new FileReader (file));
	    String         line = null;
	    StringBuilder  stringBuilder = new StringBuilder();
	    String         ls = System.getProperty("line.separator");

	    while( ( line = reader.readLine() ) != null ) {
	        stringBuilder.append( line );
	        stringBuilder.append( ls );
	    }

	    String res = stringBuilder.toString();
	    res = res.replace("%INSTID%", instId);
	    res = res.replace("%USER%", user);
	    
	    
	    
	    return res;
	}


}

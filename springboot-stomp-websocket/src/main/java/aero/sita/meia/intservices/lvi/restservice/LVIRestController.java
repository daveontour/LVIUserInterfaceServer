package aero.sita.meia.intservices.lvi.restservice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


@RestController
public class LVIRestController {

	private ArrayList<String> counterlist = new ArrayList<String>();
	private HashMap<String, String> wksMap = new HashMap<String, String>();

	
	@CrossOrigin(origins = "*")
	@GetMapping("/LoginValidation")
	public String validate(@RequestParam(name="wksname") String wksname, @RequestParam(name="user") String user) throws Exception {

		String success ="<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
				+ "<loginRecord>\n"
				+ " <login success=\"true\" />\n"
				+ " <loginMsg>\n"
				+ " <text>Success</text>\n"
				+ " </loginMsg>\n"
				+ "</loginRecord>";

		String fail ="<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
				+ "<loginRecord>\n"
				+ " <login success=\"false\"/>\n"
				+ " <loginMsg>\n"
				+ " <text>Not Valid</text>\n"
				+ " </loginMsg>\n"
				+ "</loginRecord>";
		
		if (wksMap.isEmpty()) {
			loadWorkStationMap();
		}
		
		String counter = wksMap.get(wksname);

		
		if (counter == null || user == null) {
			return fail;
		}
		
		DateTime now = DateTime.now();
		DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss");

		DateTime f = now.plusMinutes(-280);
		DateTime t = now.plusMinutes(240);

		String fs = fmt.print(f);
		String ts = fmt.print(t);

		String queryString = "http://localhost:8080/JAF_RESOURCE_DETERMINATOR_API/v1/resource/allocations/COUNTER/"+counter+"?start-time="+fs+"&end-time="+ts;		
		String json = sendGetRequest(queryString);
		
		JSONParser jsonParser = new JSONParser();
		//Read JSON file
		Object obj = jsonParser.parse(json);

		JSONObject jo = (JSONObject) obj;

		JSONArray resource = (JSONArray) jo.get("resource");
		JSONObject count = (JSONObject)resource.get(0);
		JSONObject assign = (JSONObject)count.get("assignments");
		JSONArray flights = (JSONArray) assign.get("flights");
		

		boolean loginOK = false;
		if (flights == null) {
			return fail;
		}
		for (Object item : flights) {
			JSONObject flt = (JSONObject) item;
			
			String st = (String)flt.get("startTime");
			String end = (String)flt.get("endTime");
			String al = (String)flt.get("airline");
			
			if (!al.equals(user)) {
				continue;
			}
			
			DateTime ds = DateTime.parse(st, fmt);
			DateTime de = DateTime.parse(end, fmt);
			
			if (ds.isBeforeNow() && de.isAfterNow()) {
				loginOK = true;
				break;
			}
			
		}
		
		if (loginOK) {
			return success;
		} else {
			return fail;
		}
		
	}
	
	private void loadWorkStationMap() throws IOException {
		
		String prop = new String(Files.readAllBytes(Paths.get("workstations.config")), StandardCharsets.UTF_8);
		String[] lines = prop.split(System.getProperty("line.separator"));

		for (String line : lines) {
			if (line.startsWith("#")) {
				continue;
			}

			String[] fields = line.split(":");

			wksMap.put(fields[0], fields[1]);

		}
		
	}

	@CrossOrigin(origins = "*")
	@GetMapping("/getAllocations")
	public String getAllocations(@RequestParam(name="from") String from, @RequestParam(name="to") String to) throws Exception {
		return getAllocationsInternal(from,to);
	}



	@CrossOrigin(origins = "*")
	@GetMapping("/deleteTodaysAllocations")
	public String deleteAllocations() throws Exception {



		String jsonInputString = getAllocationsInternal("-1440", "1440");

		URL url = new URL("http://localhost:8080/JAF_RESOURCE_DETERMINATOR_API/v1/resource/allocations/delete");
		HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		conn.setRequestProperty("Accept","application/json");
		conn.setRequestProperty("Content-Type","application/json");
		String auth = "svcUser" + ":" + "svcPassword";
		byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
		String authHeaderValue = "Basic " + new String(encodedAuth);

		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.setRequestProperty("Authorization", authHeaderValue);
		try(OutputStream os = conn.getOutputStream()) {
			byte[] input = jsonInputString.getBytes("utf-8");
			os.write(input, 0, input.length);			
		}


		conn.connect();
		if (conn.getResponseCode() != 201) {
			System.out.println("Delete Response Code: "+conn.getResponseCode());
		}

		return "{\"status\":\"OK\"}";
	}

	private String getAllocationsInternal(String from,  String to) throws Exception{

		DateTime now = new DateTime();
		DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss");

		DateTime f = now.plusMinutes(Integer.parseInt(from));
		DateTime t = now.plusMinutes(Integer.parseInt(to));

		String fs = fmt.print(f);
		String ts = fmt.print(t);    
		URL url = new URL("http://localhost:8080/JAF_RESOURCE_DETERMINATOR_API/v1/resource/allocations/COUNTER?start-time="+fs+"&end-time="+ts);
		HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		conn.setRequestProperty("Accept","application/json");
		String auth = "svcUser" + ":" + "svcPassword";
		byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
		String authHeaderValue = "Basic " + new String(encodedAuth);

		conn.setRequestMethod("GET");
		conn.setRequestProperty("Authorization", authHeaderValue);

		conn.connect();


		String output = "";
		if (conn.getResponseCode() == 200) {
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String strCurrentLine;
			while ((strCurrentLine = br.readLine()) != null) {
				System.out.println(strCurrentLine);
				output += strCurrentLine;
			}}
		//     return new String(Files.readAllBytes(Paths.get("counter-allocations.json")), StandardCharsets.UTF_8);
		return output;
	}

	@CrossOrigin(origins = "*")
	@GetMapping("/addAllocation")
	public String addAllocation(@RequestParam(name="first") String first, 
			@RequestParam(name="last") String last, 
			@RequestParam(name="day") String day,
			@RequestParam(name="start") String start,
			@RequestParam(name="end") String end,
			@RequestParam(name="airline") String airline,
			@RequestParam(name="flight") String flight,
			@RequestParam(name="sto") String sto
			) throws Exception {

		if (counterlist.size() == 0) {
			getResources();
		}

		int sI = -1;
		int eI = -1;

		int index = 0;
		for (String c : counterlist) {
			if (c.equals(first)) {
				sI = index;
			}
			if (c.equals(last)) {
				eI = index;
			}
			index++;
		}



		List<String> counters = counterlist.subList(sI, eI+1);
		for(String counter : counters) {		
			String jsonInputString = getAddJSON(counter,airline,flight,sto,day+"T"+start+":00",day+"T"+end+":00");
			System.out.println(jsonInputString);
			sendPostRequest(jsonInputString,"http://localhost:8080/JAF_RESOURCE_DETERMINATOR_API/v1/resource/allocations/addOrUpdate");
		}        

		return "{\"status\":\"OK\"}";
	}
	@CrossOrigin(origins = "*")
	@GetMapping("/deleteAllocation")
	public String deleteAllocation(@RequestParam(name="counter") String counter, 
			@RequestParam(name="start") String start,
			@RequestParam(name="end") String end,
			@RequestParam(name="airline") String airline,
			@RequestParam(name="flight") String flight,
			@RequestParam(name="sto") String sto
			) throws Exception {

		if (counterlist.size() == 0) {
			getResources();
		}


		String jsonInputString = getAddJSON(counter,airline,flight,sto,start,end);
		System.out.println(jsonInputString);
		sendPostRequest(jsonInputString,"http://localhost:8080/JAF_RESOURCE_DETERMINATOR_API/v1/resource/allocations/delete");

		return "{\"status\":\"OK\"}";
	}

	@CrossOrigin(origins = "*")
	@GetMapping("/getResources")
	public String getResources() throws Exception {

		this.counterlist.clear();
		String json = new String(Files.readAllBytes(Paths.get("counters.json")), StandardCharsets.UTF_8);

		JSONParser jsonParser = new JSONParser();
		//Read JSON file
		Object obj = jsonParser.parse(json);

		JSONObject jo = (JSONObject) obj;

		JSONArray counterListList = (JSONArray) jo.get("resource");

		for (Object item : counterListList) {
			JSONObject c = (JSONObject) item;
			this.counterlist.add((String)c.get("code"));
		}


		return json;
	}

	@CrossOrigin(origins = "*")
	@PostMapping("/postFile")
	public String postFile(@RequestParam("fileKey") MultipartFile file,RedirectAttributes redirectAttributes) throws Exception {

		String  content = new String(file.getBytes(), StandardCharsets.US_ASCII);
		String[] lines = content.split(System.getProperty("line.separator"));

		for (String line : lines) {
			if (line.startsWith("#")) {
				continue;
			}
			System.out.println(line);
			String[] fields = line.split(",");

			String jsonInputString = getAddJSON(fields[1],fields[4],fields[5],fields[7],fields[8],fields[9]);
			sendPostRequest(jsonInputString, "http://localhost:8080/JAF_RESOURCE_DETERMINATOR_API/v1/resource/allocations/addOrUpdate");

		}

		return "{\"status\":\"OK\"}";
	}

	private String getAddJSON(String counter,String airline, String  flightNumber, String sto, String startTime, String endTime) {

		String template ="{ \"resource\": [\n"
				+ "    {\n"
				+ "      \"area\": \"Area 1\",\n"
				+ "      \"assignments\": {\n"
				+ "        \"flights\": [\n"
				+ "          {\n"
				+ "            \"airline\": \"@airline\",\n"
				+ "            \"endTime\": \"@endTime\",\n"
				+ "            \"flightNumber\": \"@flightNum\",\n"
				+ "            \"id\": \"@id\",\n"
				+ "            \"nature\": \"Departure\",\n"
				+ "            \"scheduleTime\": \"@sto\",\n"
				+ "            \"startTime\": \"@startTime\"\n"
				+ "          }\n"
				+ "        ]\n"
				+ "      },\n"
				+ "      \"code\": \"@counter\",\n"
				+ "      \"externalName\": \"@counter\",\n"
				+ "      \"resourceType\": \"COUNTER\"\n"
				+ "    }\n"
				+ "  ] }";

		String id = counter+"-"+airline+flightNumber+sto+":"+startTime+"->"+endTime;
		String message = template.replace("@airline", airline)
				.replace("@startTime", startTime)
				.replace("@endTime", endTime)
				.replace("@flightNum", flightNumber)
				.replace("@sto", sto)
				.replace("@id", id)
				.replace("@counter", counter);

		return message;

	}

	private boolean sendPostRequest(String jsonInputString, String u) throws Exception{
		URL url = new URL(u);
		HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		conn.setRequestProperty("Accept","application/json");
		conn.setRequestProperty("Content-Type","application/json");
		conn.setDoOutput(true);
		String auth = "svcUser" + ":" + "svcPassword";
		byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
		String authHeaderValue = "Basic " + new String(encodedAuth);

		conn.setRequestMethod("POST");
		conn.setRequestProperty("Authorization", authHeaderValue);
		try(OutputStream os = conn.getOutputStream()) {
			byte[] input = jsonInputString.getBytes("utf-8");
			os.write(input, 0, input.length);			
		}


		conn.connect();
		if (conn.getResponseCode() != 201 && conn.getResponseCode() != 200) {
			System.out.println("Response Code: "+conn.getResponseCode());
			return false;
		} else {
			System.out.println("Response Code: 201");
			return true;
		}

	}
	private String sendGetRequest(String u) throws Exception{
		URL url = new URL(u);
		HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		conn.setRequestProperty("Accept","application/json");
		String auth = "svcUser" + ":" + "svcPassword";
		byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
		String authHeaderValue = "Basic " + new String(encodedAuth);

		conn.setRequestMethod("GET");
		conn.setRequestProperty("Authorization", authHeaderValue);


		conn.connect();
		String output = "";
		if (conn.getResponseCode() == 200 || conn.getResponseCode() == 201) {
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String strCurrentLine;
			while ((strCurrentLine = br.readLine()) != null) {
				output += strCurrentLine;
			}}
		
		return output;

	}
}
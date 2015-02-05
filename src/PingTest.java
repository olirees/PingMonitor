// PingTest
//
// Author: O.Rees
// Initial Revision: 2015-02-05
//
// Example output:
//
//   name=Custom Metrics|Network|Ping|<target>|<src>|min,value=...
//   name=Custom Metrics|Network|Ping|<target>|<src>|max,value=...
//   name=Custom Metrics|Network|Ping|<target>|<src>|avg,value=...
//   name=Custom Metrics|Network|Ping|<target>|<src>|stddev,value=...
//   name=Custom Metrics|Network|Ping|<target>|<src>|pktloss,value=...
//   name=Custom Metrics|Network|Ping|<target>|<src>|status,value=...

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.net.InetAddress;
import java.net.UnknownHostException;
 
public class PingTest
{
	static String pingExe = "/sbin/ping";
	static String configFile = "PingTest.cfg";
	static String prefix = "Custom Metrics|Network|Ping";
	static String srcHost = getSrcHost();

	static int defCount = 5;
	static int defTimeout = 5;
	static int defPacketSize = 0;
	static int nThreads = 5;
	static int debugLevel = 0;

	public static void main(String args[])
		throws InterruptedException
	{
		processArgs(args);
		
		new PingTest().launch();
	}

	public void launch()
	{
 		BlockingQueue<PingData> q = new ArrayBlockingQueue<>(20);

		// Launch manager thread
		PingManager pm = new PingManager(q);
		Thread pmThread = new Thread(pm);
		pmThread.setName("Manager");
		pmThread.start();

		// Launch worker threads
		ArrayList<Thread> pwList = new ArrayList<Thread>(nThreads);
		for(int i=0; i<nThreads; i++)
		{
			Thread pwThread = new Thread(new PingWorker(q));
			pwList.add(pwThread);
			pwThread.setName("Worker-" + i);
			pwThread.start();
		}

		while (true)
		{
			try {
				Thread.sleep(1000);
			} catch(InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
	}

	public static void processArgs(String argString[])
		throws IllegalArgumentException
	{
		for (String s: argString)
		{
			// Get first two chars of argument (e.g. "-d")
			String option = s.substring(0,2);

			if(option.equals("-d"))
			{
            			if (s.length() > 2) { debugLevel = Integer.parseInt(s.substring(2)); }
				else { debugLevel = 1; }
			}
			else if(option.equals("-f"))
			{
            			if (s.length() > 2) { configFile = s.substring(2); }
			}
			else if(option.equals("-p"))
			{
            			if (s.length() > 2) { prefix = s.substring(2); }
			}
			else if(option.equals("-x"))
			{
            			if (s.length() > 2) { pingExe = s.substring(2); }
			}
			else
			{
				throw new IllegalArgumentException("unreconised argument: " + s);
			}
		}
	}

	// Display message prefixed with thread name
	static void threadMessage(String message)
	{
		if(debugLevel == 0) { return; }

		String threadName = Thread.currentThread().getName();
		System.err.format("%s: %s%n", threadName, message);
	}

	static String getSrcHost()
	{
        	InetAddress ip;
        	String hostname = "localhost";
        	try {
            		ip = InetAddress.getLocalHost();
            		hostname = ip.getHostName();

        	} catch (UnknownHostException e) {
            		e.printStackTrace();
        	}

		return(hostname);
    	}


	public class PingData {

		private String src = srcHost;
		private String dst = "";
		private String iface = "";
		private int count = defCount;
		private int timeout = defTimeout;
		private int pktsize = defPacketSize;

		private int min = 0;
		private int max = 0;
		private int avg = 0;
		private int stddev = 0;
		private int pktloss = 0;
		private int status = -1;    // Default

		public String get_src() { return this.src; }
		public String get_dst() { return this.dst; }
		public String get_iface() { return this.iface; }
		public int get_count() { return this.count; }
		public int get_timeout() { return this.timeout; }
		public int get_pktsize() { return this.pktsize; }

		public int get_min() { return this.min; }
		public int get_max() { return this.max; }
		public int get_avg() { return this.avg; }
		public int get_stddev() { return this.stddev; }
		public int get_pktloss() { return this.pktloss; }
		public int get_status() { return this.status; }

		public void set_src(String val) { this.src = val; }
		public void set_dst(String val) { this.dst = val; }
		public void set_iface(String val) { this.iface = val; }
		public void set_count(int val) { this.count = val; }
		public void set_timeout(int val) { this.timeout = val; }
		public void set_pktsize(int val) { this.pktsize = val; }

		public void set_min(int val) { this.min = val; }
		public void set_max(int val) { this.max = val; }
		public void set_avg(int val) { this.avg = val; }
		public void set_stddev(int val) { this.stddev = val; }
		public void set_pktloss(int val) { this.pktloss = val; }
		public void set_status(int val) { this.status = val; }

		PingData()
		{
			// Allow default initialisation
		}

		PingData(String d, int c, int t)
		{
			this.dst = d;
			this.count = c;
			this.timeout = t;
		}

		PingData(String s, String i, String d, int c, int t, int p)
		{	
			if(s != "") { this.src = s; }
			this.dst = d;
			this.iface = i;
			this.count = c;
			this.timeout = t;
			this.pktsize = p;
		}
	}

	public class PingManager
		 implements Runnable
	{
		private BlockingQueue<PingData> queue;

		PingManager(BlockingQueue<PingData> q)
		{
			this.queue = q;
		}

		public void run()
		{
			int now;
			int lastrun = 60;

			ArrayList<PingData> cfg = readConfigFile();

			try {
				while (true)
				{	
					// Run all tests once per minute
					now = Calendar.getInstance().get(Calendar.MINUTE);

					if (now != lastrun)
					{
						lastrun = now;

						threadMessage("Running tests minute=" + now);

						for (PingData p : cfg)
						{
							threadMessage("Adding to queue... remainingCapacity=" + queue.remainingCapacity());
							queue.put(p);
						}
					}
 
					try {
						Thread.sleep(1000);
					} catch(InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				}
			} catch (InterruptedException ex) {
				threadMessage("Queue write interrupted");
		 	}
		}

		public ArrayList<PingData> readConfigFile()
		{
			ArrayList<PingData> cfg = new ArrayList<PingData>();

			// Read configuration
			threadMessage("Reading configuration file: " + configFile);
			try(BufferedReader br = new BufferedReader(new FileReader(configFile)))
			{
        			String line = br.readLine();
        			while (line != null)
				{
					// Skip comments
					Pattern pSkip = Pattern.compile("^#.*|^\\s*$");
					Matcher mSkip = pSkip.matcher(line);
					if(! mSkip.find())
					{
						PingData pd = new PingData();

						String[] params = line.split("\\s+");
 						for (int i = 0; i < params.length; i++)
						{
							String[] p = params[i].split("=");
							switch (p[0])
							{
            							case "dst":  pd.set_dst(p[1]);
                     						break;
            							case "count":  pd.set_count(Integer.parseInt(p[1]));
                     						break;
            							case "timeout":  pd.set_timeout(Integer.parseInt(p[1]));
                     						break;
            							case "pktsize":  pd.set_pktsize(Integer.parseInt(p[1]));
                     						break;
            							case "iface":  pd.set_iface(p[1]);
                     						break;
							}
						}
						cfg.add(pd);
            				}
            				line = br.readLine();
 				}
 			} catch (IOException ex) {
				threadMessage("IO exception: " + ex);
    			}	

			return(cfg);
		}
 	}

	public class PingWorker
		implements Runnable 
	{
		private final BlockingQueue<PingData> queue;

		PingWorker(BlockingQueue<PingData> q)
		{
			this.queue = q;
		}

		public void run()
		{
			try {
   				while (true) { consume(queue.take()); }
 			} catch (InterruptedException ex) {
				threadMessage("Queue read interrupted");
			}
   		}

   		void consume(PingData ref)
		{
			threadMessage("Running ping test to " + ref.get_dst());

			try {
				doPing(ref);
			} catch (IOException e) {
	            		threadMessage("IO exception...");
			}

			String srcName = ref.get_src();
			if(ref.get_iface() != "") { srcName += "/" + ref.get_iface(); }

			String metricPath = String.format("name=%s|%s|%s", prefix, ref.get_dst(), srcName);

            		System.out.println(metricPath + "|min,value=" + ref.get_min());
            		System.out.println(metricPath + "|max,value=" + ref.get_max());
            		System.out.println(metricPath + "|avg,value=" + ref.get_avg());
            		System.out.println(metricPath + "|stddev,value=" + ref.get_stddev());
            		System.out.println(metricPath + "|pktloss,value=" + ref.get_pktloss());
            		System.out.println(metricPath + "|status,value=" + ref.get_status());
		}
	}

	public void doPing(PingData t)
		throws IOException
	{
		// create the ping command as a list of strings
		List<String> commands = new ArrayList<String>();
		commands.add(pingExe);
		commands.add("-c" + t.get_count());
		commands.add("-t" + t.get_timeout());
		if(! t.get_iface().equals(""))
		{
			commands.add("-I" + t.get_iface());
		}
		if( t.get_pktsize() != 0 )
		{
			commands.add("-s" + t.get_pktsize());
		}
		commands.add(t.get_dst());

		String s = null;
		ProcessBuilder pb = new ProcessBuilder(commands);
            	threadMessage("Command " + pb.command());
		Process process = pb.start();
		BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
		BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
		
		// Example output should include:
		// 2 packets transmitted, 2 packets received, 0.0% packet loss
		// round-trip min/avg/max/stddev = 19.367/19.915/20.462/0.547 ms

		// Read the output from the command
		while ((s = stdInput.readLine()) != null)
		{
			Pattern pattern1 = Pattern.compile("round-trip min/avg/max/stddev = (\\d+)\\.\\d+/(\\d+)\\.\\d+/(\\d+)\\.\\d+/(\\d+)\\.\\d+ ms");
			Matcher matcher1 = pattern1.matcher(s);
			if(matcher1.find())
			{
				t.set_min(Integer.parseInt(matcher1.group(1)));
				t.set_max(Integer.parseInt(matcher1.group(2)));
				t.set_avg(Integer.parseInt(matcher1.group(3)));
				t.set_stddev(Integer.parseInt(matcher1.group(4)));
				continue;
			}

			Pattern pattern2 = Pattern.compile(".+ ([0-9]+)\\.([0-9]+)% packet loss");
			Matcher matcher2 = pattern2.matcher(s);
			if(matcher2.find())
			{
				int pktloss = Integer.parseInt(matcher2.group(1));
				int fraction = Integer.parseInt(matcher2.group(2));

				// Ensure loss of less than a percent shows up
				if((pktloss == 0) && (fraction > 0)) { pktloss = 1; }

				t.set_pktloss(pktloss);
				continue;
			}
		}

		// Read any errors from the attempted command
		while ((s = stdError.readLine()) != null)
		{
			//System.out.println(s);
			threadMessage(s);
		}

		// Default to problem...
		int status = 1;

		// Ensure process completed before read exit code
		try {
			status = process.waitFor();
		} catch(InterruptedException ex) {
		}

		t.set_status(status);
	}
}


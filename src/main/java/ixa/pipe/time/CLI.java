package ixa.pipe.time;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import org.jdom2.JDOMException;

import ixa.kaflib.KAFDocument;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class CLI{
	
	 public static void main(String[] args) throws IOException, JDOMException, ArgumentParserException{
		
		 Namespace parsedArguments = null;

	        // create Argument Parser
	        ArgumentParser parser = ArgumentParsers.newArgumentParser(
	            "ixa.pipe.time.heideltime").description(
	            "ixa.pipe.time.heideltime is a wrapper that reads and writes NAF. "
	                + "It parser the document and identifies the time expressions based on HeidelTime.\n");

	        // specify mapping file
	        parser
	            .addArgument("-m", "--mapping")
	            .required(true)
	            .help(
	                "It is REQUIRED to specify the file where the mapping between the used POS tagger and Treetager are provided.");
	        parser.addArgument("-c", "--config")
	        	.required(true)
	        	.help("It is REQUIRED to specify the location of the configuration file used by HeidelTime");
	        /*
	         * Parse the command line arguments
	         */

	        // catch errors and print help
	        try {
	          parsedArguments = parser.parseArgs(args);
	        } catch (ArgumentParserException e) {
	          parser.handleError(e);
	          System.out
	              .println("Run java -jar target/ixa.pipe.time.heideltime.jar -help for details");
	          System.exit(1);
	        }
		 
	        String mapping = parsedArguments.getString("mapping");
	    	String config = parsedArguments.getString("config");
	        
		// Input
		BufferedReader stdInReader = null;
		// Output
		BufferedWriter w = null;
		
		stdInReader = new BufferedReader(new InputStreamReader(System.in,"UTF-8"));
		w = new BufferedWriter(new OutputStreamWriter(System.out,"UTF-8"));
		KAFDocument kaf = KAFDocument.createFromStream(stdInReader);
		

		
		String version = CLI.class.getPackage().getImplementationVersion();
		String commit = CLI.class.getPackage().getSpecificationVersion();
		String lang = kaf.getLang();
		
		KAFDocument.LinguisticProcessor lp = kaf.addLinguisticProcessor("timeExpressions", "ixa-pipe-heideltime-" + lang, version + "-" + commit);
		lp.setBeginTimestamp();
		
		try { 
			IXAPipeHeidelTime time = new IXAPipeHeidelTime(lang,mapping,config);
			time.process(kaf);
		}
		catch (Exception e){
		      System.err.println("IXAPipe-HeidelTime failed: ");
		      e.printStackTrace();
		}
		finally {
		    lp.setEndTimestamp();
		    w.write(kaf.toString());
		    w.close();
		}

	 }

}

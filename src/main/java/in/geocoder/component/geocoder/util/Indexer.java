package in.geocoder.component.geocoder.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.SolrInputDocument;

public class Indexer {
	public static void main(String[] args) throws IOException, SolrServerException {
		SolrServer server = new HttpSolrServer("http://localhost:8983/solr/collection1");

		server.deleteByQuery("*:*");
		server.commit();

		Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();
		
		// Data source: http://download.geonames.org/export/dump/US.zip
		BufferedReader br = new BufferedReader(new FileReader("/data/US.txt"));
		String line; 
		int counter = 0;
		while ((line=br.readLine())!=null) {
			if (++counter%100000==0)
				System.out.println(counter+": "+line);
			String fields[] = line.split("\\t");
			String id = fields[0];
			String name = fields[1];
			String ascii = fields[2];
			String altNames = fields[3];
			String lat = fields[4];
			String lng = fields[5];
			String featureClass = fields[6];
			String featureCode = fields[7];
			String countryCode = fields[8];
			String admin1 = fields[10];
			String admin2 = fields[11];

			SolrInputDocument doc = new SolrInputDocument();
			doc.addField("id", id);
			doc.addField("name", name);
			doc.addField("name", ascii);
			for (String alt: altNames.split(","))
				doc.addField("name", alt);
			
			doc.addField("geo", lat+","+lng);

			// if state record
			if ("A".equals(featureClass) && "ADM1".equals(featureCode)) {
				doc.addField("admin1", countryCode);
				doc.addField("admin2", name);	
				doc.addField("admin2", ascii);	
				for (String alt: altNames.split(","))
					doc.addField("admin2", alt);

				doc.addField("admin2", admin1);	
				doc.addField("level", 2);
				docs.add(doc);
			}
			
			else if ("P".equals(featureClass) && featureCode.startsWith("PPL")) {
				doc.addField("admin1", countryCode);
				doc.addField("admin2", admin1);	
				doc.addField("admin3", admin2);

				doc.addField("admin4", name);	
				doc.addField("admin4", ascii);
				for (String alt: altNames.split(","))
					doc.addField("admin4", alt);
				doc.addField("level", 4);
				docs.add(doc);
			}
			
			if (docs.size()>0 && docs.size()%1000==0) {
				server.add(docs);
				docs.clear();
			}
		}
		
		server.add(docs);
		System.out.println("Committing...");
		server.commit();
		System.out.println("Done.");
		
		br.close();
	}
}

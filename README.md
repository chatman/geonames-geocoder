geonames-geocoder
=================

A Solr SearchComponent for geocoding based on a pre-initialized set of geocoding grammar rules. The index can be populated with GeoNames or OpenStreetMap etc. datasets. 

Setup
-----
* Install Solr 4.6.0, create a new collection with solrconfig.xml and schema.xml.
* Download US data from GeoNames (http://download.geonames.org/export/dump/US.zip) and unzip it.
* mvn package
* Copy target/geonames-geocoder-0.0.1-SNAPSHOT.jar into WEB-INF/libs directory of your solr instance.
* Run Solr.
* Run the Indexer:
<code>java -cp geonames-geocoder-0.0.1.jar in.geocoder.component.geocoder.util.Indexer US.txt http://localhost:8983/solr/collection1</code>
* Try out queries: http://localhost:8983/solr/collection1/geocoder?q=pizza+near+san+francisco,+ca

License
-------
Apache 2.0
http://www.apache.org/licenses/LICENSE-2.0

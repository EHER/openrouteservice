/*
 *  Licensed to GIScience Research Group, Heidelberg University (GIScience)
 *
 *   http://www.giscience.uni-hd.de
 *   http://www.heigit.org
 *
 *  under one or more contributor license agreements. See the NOTICE file
 *  distributed with this work for additional information regarding copyright
 *  ownership. The GIScience licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package heigit.ors.routing.graphhopper.extensions.reader.borders;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import heigit.ors.geojson.GeometryJSON;
import heigit.ors.util.CSVUtility;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.*;

public class CountryBordersReader {
    private static final Logger LOGGER = Logger.getLogger(CountryBordersReader.class);

    public static final String INTERNATIONAL_NAME = "INTERNATIONAL";
    public static final String INTERNATIONAL_ID = "-1";

    private final String BORDER_FILE;
    private final String NAME_FIELD;
    private final String HIERARCHY_ID_FIELD;

    private final String IDS_PATH;
    private final String OPEN_PATH;

    private HashMap<String, CountryInfo> ids = new HashMap<>();
    private HashMap<String, ArrayList<String>> openBorders = new HashMap<>();

    private HashMap<Long, CountryBordersHierarchy> hierarchies = new HashMap<>();

    /**
     * Empty constructor which does not read any data - the user must explicitly pass information
     */
    public CountryBordersReader() {
        BORDER_FILE = "";
        NAME_FIELD = "name";
        HIERARCHY_ID_FIELD = "hierarchy";
        IDS_PATH = "";
        OPEN_PATH = "";
    }

    /**
     * Create a CountryBordersReader object and read in data for borders, ids and open borders.
     *
     * @param filepath      Path to the borders (polygon) data
     * @param idsPath       Path to a csv file containing numeric identifiers for countries (and english name)
     * @param openPath      Path to a csv file containing pairs of country names which have open borders
     */
    public CountryBordersReader(String filepath, String idsPath, String openPath) throws IOException {
        BORDER_FILE = filepath;
        NAME_FIELD = "name";
        HIERARCHY_ID_FIELD = "hierarchy";

        IDS_PATH = idsPath;
        OPEN_PATH = openPath;

        try {
            JSONObject data = readBordersData();
            LOGGER.info("Border geometries read");

            createGeometries(data);

            readIds();
            LOGGER.info("Border ids data read");

            readOpenBorders();
            LOGGER.info("Border openness data read");
        } catch (IOException ioe) {
            // Problem with reading the data
            LOGGER.error("Could not access file(s) required for border crossing analysis");
            throw ioe;
        }
    }

    public void addHierarchy(Long id, CountryBordersHierarchy hierarchy) {
        if(!hierarchies.containsKey(id)) {
            hierarchies.put(id, hierarchy);
        }
    }

    public void addId(String id, String localName, String englishName) {
        if(!ids.containsKey(localName)) {
            ids.put(localName, new CountryInfo(id, localName, englishName));
        }
    }

    /**
     * Add an open border entry to the list of open borders. An entry for both directions will be created if it does not
     * alread exist (i.e. passing Germany & France will result in two entries - Germany->France and France->Germany).
     *
     * @param country1
     * @param country2
     */
    public void addOpenBorder(String country1, String country2) {
        if(openBorders.containsKey(country1)) {
            // The key exists, so now add the second country if it is not present
            if(!openBorders.get(country1).contains(country2)) {
                openBorders.get(country1).add(country2);
            }
        } else {
            ArrayList<String> c2 = new ArrayList<>();
            c2.add(country2);
            openBorders.put(country1, c2);
        }

        if(openBorders.containsKey(country2)) {// The key exists, so now add the second country if it is not present
            if(!openBorders.get(country2).contains(country1)) {
                openBorders.get(country2).add(country1);
            }
        } else {
            ArrayList<String> c1 = new ArrayList<>();
            c1.add(country1);
            openBorders.put(country2, c1);
        }
    }

    /**
     * Method to read the geometries from a GeoJSON file that represent the boundaries of different countries. Ideally
     * it should be written using many small objects split into hierarchies.
     *
     * If the file is a .tar.gz format, it will decompress it and then store the reulting data to be read into the
     * JSON object.
     *
     * @return      A (Geo)JSON object representing the contents of the file
     */
    private JSONObject readBordersData() throws IOException {
        String data = "";

        InputStream is = null;
        BufferedReader buf = null;
        try {
            is = new FileInputStream(BORDER_FILE);

            if(BORDER_FILE.endsWith(".tar.gz")) {
                // We are working with a compressed file
                TarArchiveInputStream tis = new TarArchiveInputStream(
                        new GzipCompressorInputStream(
                                new BufferedInputStream(is)
                        )
                );

                TarArchiveEntry entry;
                StringBuilder sb = new StringBuilder();

                while((entry = tis.getNextTarEntry()) != null) {
                    if(!entry.isDirectory()) {
                        byte[] bytes = new byte[(int) entry.getSize()];
                        tis.read(bytes);
                        String str = new String(bytes);
                        sb.append(str);
                    }
                }
                data = sb.toString();
            } else {
                // Assume a normal file so read line by line

                buf = new BufferedReader(new InputStreamReader(is));

                String line = "";
                StringBuilder sb = new StringBuilder();

                while ((line = buf.readLine()) != null) {
                    sb.append(line);
                }

                data = sb.toString();
            }
        } catch (IOException ioe) {
            LOGGER.warn("Cannot access borders file!");
            throw ioe;
        } finally {
            try {
                if(is != null)
                    is.close();
                if(buf != null)
                    buf.close();
            } catch (IOException ioe) {
                LOGGER.warn("Error closing file reader buffers!");
            } catch (NullPointerException npe) {
                // This can happen if the file itself wasn't available
                throw new IOException("Borders file " + BORDER_FILE + " not found!");
            }
        }

        JSONObject json = new JSONObject(data);

        return json;
    }

    /**
     * Generate geometries from a GeoJSON object. These CountryBordersPolygons are stored in CountryBordersHierarchy
     * objects to speed up the searching process.
     *
     * @param json
     */
    private void createGeometries(JSONObject json) {
        JSONArray features = json.getJSONArray("features");

        int objectCount = 0;
        int hierarchyCount = 0;

        int len = features.length();

        for(int i=0; i<len; i++) {
            try {
                JSONObject obj = features.getJSONObject(i);
                Geometry geom = GeometryJSON.parse(obj.getJSONObject("geometry"));

                // Also need the id of the country and its hierarchy id
                String id = obj.getJSONObject("properties").getString(NAME_FIELD);

                Long hId = -1l;

                // If there is no hierarchy info, then we set the id of the hierarchy to be a default of 1
                if(obj.getJSONObject("properties").has(HIERARCHY_ID_FIELD))
                    hId = obj.getJSONObject("properties").getLong(HIERARCHY_ID_FIELD);

                // Create the borders object
                CountryBordersPolygon c = new CountryBordersPolygon(id, geom, hId);

                // add to the hierarchy
                if(c != null) {
                    if(!hierarchies.containsKey(hId)) {
                        hierarchies.put(hId, new CountryBordersHierarchy(hId));
                        hierarchyCount++;
                    }

                    hierarchies.get(hId).add(c);
                    objectCount++;
                }

            } catch (Exception e) {
                LOGGER.warn("Error reading country polygon from borders file!" + e.getMessage());
            }
        }

        LOGGER.info(objectCount + " countries read in " + hierarchyCount + " hiearchies");
    }

    /**
     * Method for getting a list of country objects that the given point can be found within. This could be more than
     * one if the point is found in overlapping regions.
     *
     * @param c     The point that you want to know which country is in
     * @return      An array of CountryBorderPolygons that the point is within the geometry of.
     */
    public CountryBordersPolygon[] getCountry(Coordinate c) {
        ArrayList<CountryBordersPolygon> countries = new ArrayList<>();
        Iterator it = hierarchies.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<Long, CountryBordersHierarchy> pair = (Map.Entry)it.next();
            CountryBordersHierarchy h =  pair.getValue();
            if(h.inBbox(c)) {
                // Now need to check the countries
                ArrayList<CountryBordersPolygon> ps = h.getPolygons();
                for(CountryBordersPolygon cp : ps) {
                    if(cp.inBbox(c) && cp.inArea(c)) {
                        countries.add(cp);
                    }
                }
            }
        }

        return countries.toArray(new CountryBordersPolygon[countries.size()]);
    }

    /**
     * Method for getting a list of country objects that the given point COULD be found within. This could be more than
     * one if the point is found in overlapping regions. This tests against bounding boxes, and so the countries
     * returned may not actually surround the point. The method should be used to get a quick approximation as to
     * whether the country is a candidate for containing the point.
     *
     * @param c     The point that you want to know which country is in
     * @return      An array of CountryBorderPolygons that the point is within the geometry of.
     */
    public CountryBordersPolygon[] getCandidateCountry(Coordinate c) {
        ArrayList<CountryBordersPolygon> countries = new ArrayList<>();
        Iterator it = hierarchies.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<Long, CountryBordersHierarchy> pair = (Map.Entry)it.next();
            CountryBordersHierarchy h =  pair.getValue();
            if(h.inBbox(c)) {
                // Now need to check the countries
                ArrayList<CountryBordersPolygon> ps = h.getPolygons();
                for(CountryBordersPolygon cp : ps) {
                    if(cp.inBbox(c)) {
                        countries.add(cp);
                    }
                }
            }
        }

        return countries.toArray(new CountryBordersPolygon[countries.size()]);
    }

    /**
     * Get the unique identifier of the country (read from a CSV file in the constructor)
     *
     * @param name      The local name of the country
     * @return          The unique identifier
     */
    public String getId(String name) {
        if(name.equals(INTERNATIONAL_NAME))
            return INTERNATIONAL_ID;

        if(ids.containsKey(name))
            return ids.get(name).id;
        else
            return "";
    }

    /**
     * Get the English name of the country (read from the id CSV)
     *
     * @param name      The local name of the country
     * @return          The English name of the country
     */
    public String getEngName(String name) {
        if(name.equals(INTERNATIONAL_NAME))
            return INTERNATIONAL_NAME;

        if(ids.containsKey(name))
            return ids.get(name).nameEng;
        else
            return "";
    }

    /**
     * Get whether a border between two specified countries is open or closed
     *
     * @param c1        The first country of the border (English name)
     * @param c2        The second country of the border (English name)
     * @return
     */
    public boolean isOpen(String c1, String c2) {
        if(openBorders.containsKey(c1)) {
            return openBorders.get(c1).contains(c2);
        } else if(openBorders.containsKey(c2))
            return openBorders.get(c2).contains(c1);

        return false;
    }

    /**
     * Read information from the id csv. This includes a unique identifier, the local name of the country and the
     * English name of the country.
     */
    private void readIds() {
        // First read the csv file
        ArrayList<ArrayList<String>> data = CSVUtility.readFile(IDS_PATH);

        // Loop through and store in the hashmap
        for(ArrayList<String> col : data) {
            if(col.size() == 3) {
                ids.put(col.get(1), new CountryInfo(col.get(0), col.get(1), col.get(2)));
            }
        }
    }

    /**
     * Read information about whether a border between two countries is open. If a border is in the file, then it is
     * an open border.
     */
    private void readOpenBorders() {
        // First read the csv file
        ArrayList<ArrayList<String>> data = CSVUtility.readFile(OPEN_PATH);

        // Loop through and store in the hashmap
        for(ArrayList<String> col : data) {
            if(col.size() == 2) {
                // See if there is already the start country
                if(!openBorders.containsKey(col.get(0))) {
                    openBorders.put(col.get(0), new ArrayList<>());
                }
                openBorders.get(col.get(0)).add(col.get(1));
            }
        }
    }

    /**
     * Holder class for storing information about a country read from the ids csv.
     */
    private class CountryInfo {
        public String id;
        public String name;
        public String nameEng;

        public CountryInfo(String id, String name, String nameEng) {
            this.id = id;
            this.name = name;
            this.nameEng = nameEng;
        }
    }


}

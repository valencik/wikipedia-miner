package org.wikipedia.miner.util;

import org.wikipedia.miner.model.*;
import java.io.*;
import java.util.Properties;

public class UploadDump {

    public static void main(String[] args) throws Exception {
        WikipediaDatabase tmp;
        File dumpdir;
        File prop_file;
        Properties my_properties;

        my_properties = new Properties();

        dumpdir = (new File(args[0])).getAbsoluteFile();
        prop_file = new File(dumpdir, "config.xml");

        if (prop_file.isFile() && prop_file.canRead()) {
            FileInputStream prop_file_inputstream;
            prop_file_inputstream = new FileInputStream(prop_file);
            my_properties.loadFromXML(prop_file_inputstream);
            dumpdir = new File(my_properties.getProperty("wikipediaminer_dump_path", dumpdir.getAbsolutePath()));
        } else {
            my_properties.setProperty("db_host", "");
            my_properties.setProperty("db_name", "");
            my_properties.setProperty("db_user", "");
            my_properties.setProperty("db_passwd", "");
            my_properties.setProperty("wikipediaminer_dump_path", dumpdir.getAbsolutePath());
            if (prop_file.canWrite()) {
                FileOutputStream prop_file_outputstream;
                prop_file_outputstream = new FileOutputStream(prop_file);
                my_properties.storeToXML(prop_file_outputstream, null);
                System.out.println("Please fill \"" + prop_file + "\" with right information.");
                return;
            }
        }

        tmp = new WikipediaDatabase(
                my_properties.getProperty("db_host"),
                my_properties.getProperty("db_name"),
                my_properties.getProperty("db_user"),
                my_properties.getProperty("db_passwd"));
        tmp.loadData(new File(my_properties.getProperty("wikipediaminer_dump_path")), true);
    }
}
package utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Props {
    private static final String PROPS_FILE = "./shorts.props";

    public static String getValue(String key) {
        try {
            Properties props = new Properties();
            FileInputStream in = new FileInputStream(PROPS_FILE);
            props.load(in);
            in.close();
            return props.getProperty(key);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}


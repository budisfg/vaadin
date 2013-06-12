package com.vaadin.tests.tb3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameters;
import org.openqa.selenium.remote.DesiredCapabilities;

@RunWith(value = TB3Runner.class)
// TB3Runner automatically calls setDesiredCapabilities before the test is run
public abstract class MultiBrowserTest extends PrivateTB3Configuration {

    private static List<DesiredCapabilities> allBrowsers = new ArrayList<DesiredCapabilities>();
    static {
        allBrowsers.add(ie(8));
        allBrowsers.add(ie(9));
        allBrowsers.add(ie(10));
        allBrowsers.add(firefox(17));
        // browsers.add(safari(6));
        allBrowsers.add(chrome(21));
        allBrowsers.add(opera(12));
    }

    @Parameters
    public static Collection<DesiredCapabilities> getAllBrowsers() {
        return Collections.unmodifiableCollection(allBrowsers);
    }

    protected static DesiredCapabilities safari(int version) {
        DesiredCapabilities c = DesiredCapabilities.safari();
        c.setVersion("" + version);
        return c;
    }

    protected static DesiredCapabilities chrome(int version) {
        DesiredCapabilities c = DesiredCapabilities.chrome();
        // c.setVersion("" + version);
        return c;
    }

    protected static DesiredCapabilities opera(int version) {
        DesiredCapabilities c = DesiredCapabilities.opera();
        c.setVersion("" + version);
        return c;
    }

    protected static DesiredCapabilities firefox(int version) {
        DesiredCapabilities c = DesiredCapabilities.firefox();
        c.setVersion("" + version);
        return c;
    }

    protected static DesiredCapabilities ie(int version) {
        DesiredCapabilities c = DesiredCapabilities.internetExplorer();
        c.setVersion("" + version);
        return c;
    }
}
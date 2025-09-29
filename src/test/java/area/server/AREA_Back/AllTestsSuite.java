package area.server.AREA_Back;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages({
    "area.server.AREA_Back.entity",
    "area.server.AREA_Back.dto",
    "area.server.AREA_Back.repository",
    "area.server.AREA_Back.controller"
})
public class AllTestsSuite {
    // This class runs all tests for coverage reporting
}
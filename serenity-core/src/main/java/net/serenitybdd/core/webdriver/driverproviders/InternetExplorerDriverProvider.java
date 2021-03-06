package net.serenitybdd.core.webdriver.driverproviders;

import net.serenitybdd.core.buildinfo.DriverCapabilityRecord;
import net.serenitybdd.core.di.WebDriverInjectors;
import net.serenitybdd.core.time.InternalSystemClock;
import net.serenitybdd.core.webdriver.servicepools.DriverServicePool;
import net.serenitybdd.core.webdriver.servicepools.InternetExplorerServicePool;
import net.thucydides.core.fixtureservices.FixtureProviderService;
import net.thucydides.core.guice.Injectors;
import net.thucydides.core.steps.StepEventBus;
import net.thucydides.core.util.EnvironmentVariables;
import net.thucydides.core.webdriver.CapabilityEnhancer;
import net.thucydides.core.webdriver.stubs.WebDriverStub;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.MutableCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static net.thucydides.core.webdriver.SupportedWebDriver.IEXPLORER;

public class InternetExplorerDriverProvider implements DriverProvider {

    private final DriverCapabilityRecord driverProperties;
    private static final Logger LOGGER = LoggerFactory.getLogger(InternetExplorerDriverProvider.class);

    private final DriverServicePool driverServicePool = new InternetExplorerServicePool();
    private final EnvironmentVariables environmentVariables;

    private DriverServicePool getDriverServicePool() throws IOException {
        driverServicePool.ensureServiceIsRunning();
        return driverServicePool;
    }

    private final FixtureProviderService fixtureProviderService;

    public InternetExplorerDriverProvider(FixtureProviderService fixtureProviderService) {
        this.fixtureProviderService = fixtureProviderService;
        this.driverProperties = WebDriverInjectors.getInjector().getInstance(DriverCapabilityRecord.class);
        this.environmentVariables = Injectors.getInjector().getInstance(EnvironmentVariables.class);
    }

    @Override
    public WebDriver newInstance(String options, EnvironmentVariables environmentVariables) {
        if (StepEventBus.getEventBus().webdriverCallsAreSuspended()) {
            return new WebDriverStub();
        }

        CapabilityEnhancer enhancer = new CapabilityEnhancer(environmentVariables, fixtureProviderService);
        MutableCapabilities mutableCapabilities = enhancer.enhanced(recommendedDefaultInternetExplorerCapabilities(), IEXPLORER);

        SetProxyConfiguration.from(environmentVariables).in(mutableCapabilities);

        driverProperties.registerCapabilities("iexplorer", capabilitiesToProperties(mutableCapabilities));

        try {
            return retryCreateDriverOnNoSuchSession(mutableCapabilities);
        } catch (Exception couldNotStartServer) {
            LOGGER.warn("Failed to start the Internet driver service, using a native driver instead - " + couldNotStartServer.getMessage());
            return new InternetExplorerDriver(mutableCapabilities);
        }
    }

    private WebDriver retryCreateDriverOnNoSuchSession(MutableCapabilities mutableCapabilities) throws IOException {
        return new TryAtMost(3).toStartNewDriverWith(mutableCapabilities);
    }

    private class TryAtMost {
        private final int maxTries;

        private TryAtMost(int maxTries) {
            this.maxTries = maxTries;
        }

        public WebDriver toStartNewDriverWith(MutableCapabilities MutableCapabilities) throws IOException {
            try {
                return getDriverServicePool().newDriver(MutableCapabilities);
            } catch (NoSuchSessionException e) {
                if (maxTries == 0) { throw e; }

                LOGGER.error(e.getClass().getCanonicalName() + " happened - retrying in 2 seconds");
                new InternalSystemClock().pauseFor(2000);
                return new TryAtMost(maxTries - 1).toStartNewDriverWith(MutableCapabilities);
            }
        }
    }

    private MutableCapabilities recommendedDefaultInternetExplorerCapabilities() {
        MutableCapabilities defaults = DesiredCapabilities.internetExplorer();

        defaults.setCapability(InternetExplorerDriver.IGNORE_ZOOM_SETTING, true);
        defaults.setCapability(InternetExplorerDriver.NATIVE_EVENTS, false);
        defaults.setCapability(InternetExplorerDriver.REQUIRE_WINDOW_FOCUS, false);
        defaults.setCapability(CapabilityType.TAKES_SCREENSHOT, true);
        defaults.setCapability(CapabilityType.SUPPORTS_JAVASCRIPT, true);

        defaults = AddCustomDriverCapabilities.from(environmentVariables).forDriver(IEXPLORER).to(defaults);
        return defaults;
    }
}

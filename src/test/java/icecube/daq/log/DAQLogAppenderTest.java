package icecube.daq.log;

import java.io.IOException;
import java.io.PrintStream;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class DAQLogAppenderTest
    extends TestCase
{
    private static final Logger LOG =
        Logger.getLogger(DAQLogAppenderTest.class);

    private static final PrintStream STDOUT = System.out;
    private static final PrintStream STDERR = System.err;

    private static final String LOGHOST = "localhost";
    private static final int LOGPORT = 6666;

    private LogReader logRdr;
    private LogReader liveRdr;
    private DAQLogAppender appender;

    private void createAppender(String name, Level level, String logHost,
                                int logPort, String liveHost, int livePort)
    {
        try {
            appender = new DAQLogAppender(name, level, logHost, logPort,
                                          liveHost, livePort);
        } catch (Exception ex) {
            System.err.println("Couldn't create appender");
            ex.printStackTrace();
            appender = null;
        }

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
    }

    private void sendMsg(Level level, String msg, LogReader rdr)
    {
        if (level.isGreaterOrEqual(appender.getLevel())) {
            rdr.addExpected(msg);
        }

        if (level.equals(Level.DEBUG)) {
            LOG.debug(msg);
        } else if (level.equals(Level.INFO)) {
            LOG.info(msg);
        } else if (level.equals(Level.WARN)) {
            LOG.warn(msg);
        } else if (level.equals(Level.ERROR)) {
            LOG.error(msg);
        } else if (level.equals(Level.FATAL)) {
            LOG.fatal(msg);
        } else {
            fail("Unknown log level " + level);
        }
    }

    public static Test suite()
    {
        return new TestSuite(DAQLogAppenderTest.class);
    }

    @Override
    protected void tearDown()
    {
        if (!STDOUT.equals(System.out)) {
            System.setOut(STDOUT);
        }
        if (!STDERR.equals(System.err)) {
            System.setErr(STDERR);
        }

        if (appender != null) {
            appender.close();
        }

        if (logRdr != null) {
            logRdr.close();

            if (logRdr.hasError()) {
                fail(logRdr.getNextError());
            }

            assertEquals("Not all log messages were received",
                         0, logRdr.getNumberOfExpectedMessages());
        }

        if (liveRdr != null) {
            liveRdr.close();

            if (liveRdr.hasError()) {
                fail(liveRdr.getNextError());
            }

            assertEquals("Not all live messages were received",
                         0, liveRdr.getNumberOfExpectedMessages());
        }
    }

    private void waitForLogMessages(LogReader rdr)
    {
        for (int i = 0; !rdr.hasError() && !rdr.isFinished() && i < 10; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }
        if (rdr.hasError()) fail(rdr.getNextError());
        assertEquals("Not all log messages were received",
                     0, rdr.getNumberOfExpectedMessages());
    }

    public void testLog()
        throws IOException
    {
        logRdr = new LogReader("log");

        createAppender("noname", Level.INFO, "localhost", logRdr.getPort(),
                       null, 0);

        sendMsg(Level.INFO, "This is a test of logging.", logRdr);
        sendMsg(Level.INFO, "This is another test of logging.", logRdr);
        sendMsg(Level.WARN, "This is a WARN test.", logRdr);
        sendMsg(Level.ERROR, "This is a ERROR test.", logRdr);
        sendMsg(Level.FATAL, "This is a FATAL test.", logRdr);
        sendMsg(Level.DEBUG, "This is a DEBUG test.", logRdr);

        waitForLogMessages(logRdr);

        for (int i = 0; i < 3; i++) {
            sendMsg(Level.INFO, "This is test " + i + " of logging.", logRdr);
            waitForLogMessages(logRdr);
        }
    }

    public void testRedirect()
        throws IOException
    {
        logRdr = new LogReader("log");

        createAppender("noname", Level.INFO, "localhost", logRdr.getPort(),
                       null, 0);

        Logger errLog = Logger.getLogger("STDERR");
        LoggerOutputStream logStream =
            new LoggerOutputStream(errLog, Level.ERROR);
        System.setErr(new PrintStream(logStream));

        String errMsg = "Error";
        logRdr.addExpected(errMsg);
        System.err.println(errMsg);
        waitForLogMessages(logRdr);
    }

    public void testLogLive()
        throws IOException
    {
        logRdr = new LogReader("log");
        liveRdr = new LogReader("live");

        String[] logMsgs = new String[] { "LOG 1", "LOG 2" };

        for (int i = 1; i < 4; i++) {
            String logHost;
            int logPort;
            if ((i & 0x1) == 0x1) {
                logHost = "localhost";
                logPort = logRdr.getPort();
            } else {
                logHost = null;
                logPort = 0;
            }

            String liveHost;
            int livePort;
            if ((i & 0x2) == 0x2) {
                liveHost = "localhost";
                livePort = liveRdr.getPort();
            } else {
                liveHost = null;
                livePort = 0;
            }

            createAppender("noname", Level.INFO, logHost, logPort,
                           liveHost, livePort);

            for (int s = 0; s < logMsgs.length; s++) {
                if (logHost != null) {
                    logRdr.addExpected(logMsgs[s]);
                }
                if (liveHost != null) {
                    liveRdr.addExpected(logMsgs[s]);
                }

                LOG.error(logMsgs[s]);
            }

            waitForLogMessages(logRdr);
            waitForLogMessages(liveRdr);
        }
    }

    /**
     * Main routine which runs text test in standalone mode.
     *
     * @param args the arguments with which to execute this method.
     */
    public static void main(String[] args)
    {
        junit.textui.TestRunner.run(suite());
    }
}

package io.zulia.tools.cmd;

import io.zulia.cmd.common.ZuliaCommonCmd;
import io.zulia.cmd.common.ZuliaVersionProvider;
import io.zulia.data.output.FileDataOutputStream;
import io.zulia.data.target.spreadsheet.csv.CSVDataTarget;
import io.zulia.testing.ZuliaTestRunner;
import io.zulia.testing.config.ZuliaTestConfig;
import io.zulia.testing.result.TestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "zuliatest", versionProvider = ZuliaVersionProvider.class, scope = CommandLine.ScopeType.INHERIT)
public class ZuliaTest implements Callable<Integer> {

	private static final Logger LOG = LoggerFactory.getLogger(ZuliaTest.class);

	@CommandLine.Option(names = "--testConfig", description = "Full path to the test config yaml file", required = true)
	private String testConfig;

	@CommandLine.Option(names = "--testOutput", description = "Full path to the test output csv file", required = true)
	private String testOutput;

	@Override
	public Integer call() throws Exception {

		Path testConfigPath = Paths.get(testConfig);
		if (Files.exists(testConfigPath)) {
			Yaml yaml = new Yaml();
			try (FileInputStream fileInputStream = new FileInputStream(testConfigPath.toFile())) {
				ZuliaTestConfig zuliaTestConfig = yaml.loadAs(fileInputStream, ZuliaTestConfig.class);
				LOG.info("Running tests from <" + testConfig + ">");
				ZuliaTestRunner zuliaTestRunner = new ZuliaTestRunner(zuliaTestConfig);
				List<TestResult> testResults = zuliaTestRunner.runTests();

				LOG.info("Writing results to <" + testOutput + ">");
				FileDataOutputStream dataOutputStream = FileDataOutputStream.from(testOutput, true);
				try (CSVDataTarget csvDataTarget = CSVDataTarget.withDefaults(dataOutputStream)) {
					for (TestResult testResult : testResults) {
						csvDataTarget.writeRow(testResult.getTestId(), testResult.isPassed() ? "PASS" : "FAIL");
					}
				}

			}

		}
		else {
			System.err.println("Yaml configuration file <" + testConfig + "> does not exist or is not readable");
			System.exit(9);
		}

		return CommandLine.ExitCode.OK;
	}

	public static void main(String[] args) {
		ZuliaCommonCmd.runCommandLine(new ZuliaTest(), args);
	}

}

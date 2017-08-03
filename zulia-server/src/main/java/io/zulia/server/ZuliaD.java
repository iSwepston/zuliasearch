package io.zulia.server;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import io.zulia.server.config.IndexConfig;
import io.zulia.server.config.MongoNodeConfig;
import io.zulia.server.config.MongoServer;
import io.zulia.server.config.NodeConfig;
import io.zulia.server.config.ZuliaConfig;
import io.zulia.server.util.ServerNameHelper;
import io.zulia.server.util.log.LogUtil;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.search.BooleanQuery;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static io.zulia.message.ZuliaBase.Node;

public class ZuliaD {

	private static final Logger LOG = Logger.getLogger(ZuliaD.class.getName());

	private static final Gson GSON = new GsonBuilder().create();

	public static class ZuliaArgs {

		@Parameter(names = "--help", help = true)
		private boolean help;

		@Parameter(names = "--config", description = "Full path to the config (defaults to $APP_HOME/config/zulia.properties)")
		private String configPath = "config" + File.separator + "zulia.properties";
	}

	@Parameters
	public static class StartArgs {

	}

	@Parameters
	public static class AddNodeArgs {

	}

	@Parameters
	public static class RemoveNodeArgs {

		@Parameter(names = "--server", description = "Server to remove from cluster", required = true)
		private String server;

		@Parameter(names = "--hazelcastPort", description = "Hazelcast port of server to remove from cluster", required = true)
		private int hazelcastPort;

	}

	public static void main(String[] args) {

		LogUtil.init();

		ZuliaArgs zuliaArgs = new ZuliaArgs();
		StartArgs startArgs = new StartArgs();
		AddNodeArgs addNodeArgs = new AddNodeArgs();
		RemoveNodeArgs removeNodeArgs = new RemoveNodeArgs();

		JCommander jCommander = JCommander.newBuilder().addObject(zuliaArgs).addCommand("start", startArgs).addCommand("addNode", addNodeArgs)
				.addCommand("removeNode", removeNodeArgs).build();
		try {
			jCommander.parse(args);

			if (jCommander.getParsedCommand() == null) {
				jCommander.usage();
				System.exit(2);
			}

			String prefix = System.getenv("APP_HOME");

			String config = zuliaArgs.configPath;
			if (prefix != null) {
				config = prefix + File.separator + config;
			}

			ZuliaConfig zuliaConfig = GSON.fromJson(new FileReader(config), ZuliaConfig.class);
			LOG.info("Using config <" + config + ">");

			String dataDir = zuliaConfig.getDataPath();
			Path dataPath = Paths.get(dataDir);

			IndexConfig indexConfig = null;
			NodeConfig nodeConfig = null;

			File dataFile = dataPath.toFile();
			if (!dataFile.exists()) {
				throw new IOException("Data dir <" + dataDir + "> does not exist");
			}
			else {
				LOG.info("Using data directory <" + dataFile.getAbsolutePath() + ">");
			}

			if (zuliaConfig.getServerAddress() == null) {
				zuliaConfig.setServerAddress(ServerNameHelper.getLocalServer());
			}

			if (zuliaConfig.isCluster()) {
				List<MongoServer> mongoServers = zuliaConfig.getMongoServers();

				List<ServerAddress> serverAddressList = new ArrayList<>();

				for (MongoServer mongoServer : mongoServers) {
					serverAddressList.add(new ServerAddress(mongoServer.getHostname(), mongoServer.getPort()));
				}

				MongoProvider.setMongoClient(new MongoClient(serverAddressList));

				//indexConfig = new MongoIndexConfig(mongoServers);
				nodeConfig = new MongoNodeConfig(MongoProvider.getMongoClient(), zuliaConfig.getClusterName());
			}
			else {
				//indexConfig = new FileIndexConfig(dataPath + File.separator + "config");
				//nodeConfig = new FileNodeConfig(dataPath + File.separator + "config");
			}

			if ("start".equals(jCommander.getParsedCommand())) {
				setLuceneStatic();
				List<Node> nodes = nodeConfig.getNodes();

				if (zuliaConfig.isCluster()) {
					if (nodes.isEmpty()) {
						LOG.severe("No nodes added to the cluster");
						System.exit(3);
					}
					displayNodes(nodeConfig, "Registered nodes:");
				}
			}
			else if ("addNode".equals(jCommander.getParsedCommand())) {
				if (!zuliaConfig.isCluster()) {
					throw new IllegalArgumentException("Add node is only available in cluster mode");
				}

				Node node = Node.newBuilder().setServerAddress(zuliaConfig.getServerAddress()).setHazelcastPort(zuliaConfig.getHazelcastPort())
						.setServicePort(zuliaConfig.getServicePort()).setRestPort(zuliaConfig.getRestPort()).build();

				LOG.info("Adding node: " + formatNode(node));

				nodeConfig.addNode(node);

				displayNodes(nodeConfig, "Registered Nodes:");

			}
			else if ("removeNode".equals(jCommander.getParsedCommand())) {
				if (!zuliaConfig.isCluster()) {
					throw new IllegalArgumentException("Add node is only available in cluster mode");
				}

				Node node = Node.newBuilder().setServerAddress(removeNodeArgs.server).setHazelcastPort(removeNodeArgs.hazelcastPort).build();

				LOG.info("Removing node: " + formatNode(node));
				nodeConfig.removeNode(node);

				displayNodes(nodeConfig, "Registered Nodes:");
			}

		}
		catch (ParameterException e) {
			jCommander.usage();
			System.exit(2);
		}
		catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}

	}

	private static void displayNodes(NodeConfig nodeConfig, String header) throws InvalidProtocolBufferException {
		LOG.info(header);
		for (Node node : nodeConfig.getNodes()) {
			LOG.info("  " + formatNode(node));
		}
	}

	private static String formatNode(Node node) throws InvalidProtocolBufferException {
		JsonFormat.Printer printer = JsonFormat.printer();
		return printer.print(node).replace("\n", " ").replaceAll("\\s+", " ");
	}

	private static void setLuceneStatic() {
		BooleanQuery.setMaxClauseCount(16 * 1024);
		FacetsConfig.DEFAULT_DIM_CONFIG.multiValued = true;
	}
}

package gg.xp;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;
import jakarta.inject.Named;
import oracle.nosql.driver.values.MapValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class Backup {
	private static final Logger log = LoggerFactory.getLogger(Backup.class);
	private static final DateTimeFormatter BACKUP_DIRECTORY_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

	private final OracleNoSqlDb db;

	public Backup(@Named("primary") OracleNoSqlDb db) {
		this.db = db;
	}

	private void doBackup(File outdir) {
		if (outdir.exists()) {
			if (!outdir.isDirectory()) {
				throw new IllegalArgumentException(outdir.getAbsolutePath() + " is not a directory");
			}
		}
		else if (!outdir.mkdirs()) {
			throw new IllegalStateException("Could not create directory " + outdir.getAbsolutePath());
		}

		long rowCount = db.countRows();
		log.info("Row count: {}", rowCount);
		Path outPath = outdir.toPath();
		AtomicInteger counter = new AtomicInteger();
		db.iterateAll((MapValue value) -> {
			UUID uuid = UUID.fromString(value.get("linkuuid").getString());
			Path out = outPath.resolve(uuid + ".json");
			try {
				Files.writeString(out, value.toJson(), StandardCharsets.UTF_8);
				var count = counter.incrementAndGet();
				if (count % 100 == 0) {
					log.info("{} rows written", count);
				}
				Thread.sleep(10);
			}
			catch (IOException | InterruptedException e) {
				throw new IllegalStateException("Could not write backup entry", e);
			}
		});
		log.info("Backup completed. Total rows: {}, Expected rows: {}", counter.get(), rowCount);
	}

	static void main(String[] args) {
		log.info("Backup starting");
		try (ApplicationContext context = ApplicationContext.run()) {
			String timestamp = LocalDateTime.now().format(BACKUP_DIRECTORY_FORMAT);
			context.getBean(Backup.class).doBackup(new File("backups", timestamp));
			log.info("Backup complete");
		}
		catch (Throwable t) {
			log.error("Backup failure", t);
			System.exit(1);
		}
	}
}

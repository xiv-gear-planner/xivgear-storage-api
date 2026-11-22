package gg.xp.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.intellij.lang.annotations.Language;

import java.util.UUID;

@Controller("/share")
@Singleton
public class Share {
	private static final String DEFAULT_NAME = "XivGear - FFXIV Gear Planner";
	private static final String DEFAULT_DESC = "XivGear is an advanced and easy-to-use FFXIV gear planner/set builder with built-in simulation support.";

	private final Shortlink sl;
	private final ObjectMapper mapper;

	public Share(Shortlink sl, ObjectMapper mapper) {
		this.sl = sl;
		this.mapper = mapper;
	}

	@Get(value = "/{uuid}", produces = MediaType.TEXT_HTML)
	public MutableHttpResponse<String> getShare(@PathVariable("uuid") String path) {
		final UUID uuid;
		try {
			uuid = UUID.fromString(path);
		}
		catch (IllegalArgumentException ignored) {
			return HttpResponse.notFound();
		}

		String response = sl.getRaw(uuid);
		if (response == null) {
			return HttpResponse.notFound();
		}
		try {
			JsonNode tree = mapper.readTree(response);
			String name = tree.at("/name").textValue();
			String desc = tree.at("/description").textValue();
			if (name == null) {
				name = DEFAULT_NAME;
			}
			else {
				name = StringUtils.abbreviate(name, "…", 40) + " - " + DEFAULT_NAME;
			}
			if (desc == null) {
				desc = DEFAULT_DESC;
			}
			else {
				desc = StringUtils.abbreviate(desc, "…", 200) + "\n\n" + DEFAULT_DESC;
			}
			String redirectUrl = "https://xivgear.app/#/sl/" + path;
			return HttpResponse.ok(buildShareTemplate(name, desc, redirectUrl));
		}
		catch (Exception e) {
			throw new IllegalStateException("Could not build share page", e);
		}
	}

	@Language("HTML")
	private static String buildShareTemplate(String title, String desc, String redirectUrl) {
		title = StringEscapeUtils.escapeHtml4(title);
		desc = StringEscapeUtils.escapeHtml4(desc);
		return """
				<!DOCTYPE html>
				<html lang="en">
				<head>
				<meta charset="UTF-8">
				<meta property="og:site_name" content="XivGear"/>
				<meta property="og:type" content="website"/>
				<meta property="og:title" content="%s"/>
				<meta property="og:description" content="%s"/>
				<meta property="og:url" content="%s"/>
				<link rel="stylesheet" href="https://xivgear.app/style.css"/>
				<title>XivGear - FFXIV Gear Planner</title>
				<meta http-equiv="Refresh" content="0; url='%s'" />
				</head>
				<body>
				<div id="content-area">
				<h1>Loading... </h1>
				</div>
				</body>
				</html>
				""".formatted(title, desc, redirectUrl, redirectUrl);
	}
}

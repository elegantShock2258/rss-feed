package com.niilopoutanen.rss_feed.parser;

import static java.lang.Math.min;

import android.util.Log;

import com.niilopoutanen.rss_feed.parser.parsers.AtomParser;
import com.niilopoutanen.rss_feed.parser.parsers.RssParser;
import com.niilopoutanen.rss_feed.rss.Post;
import com.niilopoutanen.rss_feed.rss.Source;

import org.jsoup.nodes.Document;

import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Parser {
    public Source source;
    public List<Post> posts = new ArrayList<>();

    public Parser() {

    }

    public static boolean isValid(Source source) {
        if (source == null || source.url == null || source.url.isEmpty()) {
            return false;
        }
        try {
            FeedFinder feedFinder = new FeedFinder();
            feedFinder.find(source.url);
            URL result = feedFinder.getResult();
            if (result == null || source.url.isEmpty()) {
                return false;
            }
            source.url = result.toString();
        } catch (RSSException r) {
            return false;
        }

        return true;
    }

    public void load(String url) {
        if (url == null || url.isEmpty()) return;

        Document document = WebUtils.connect(url);
        parse(document);
        if (source != null) {
            source.url = url;
        }
    }

    public static List<Post> loadMultiple(List<Source> sources) {
        List<Post> posts = Collections.synchronizedList(new ArrayList<>());

        final int THREADS_WIDTH = 20; // do 20 sources per thread

        List<Source> sourceStream = sources.stream().filter(source -> source != null && source.visible).collect(Collectors.toList());
        ExecutorService es = Executors.newCachedThreadPool();

        for (int i = 0; i < sourceStream.size(); i += THREADS_WIDTH) {
            int finalI = i;
            es.submit(Executors.callable(() -> {
                for (int j = finalI; j < min(finalI + THREADS_WIDTH, sourceStream.size()); j++) {
                    Source source = sourceStream.get(j);
                    Parser parser = new Parser();
                    parser.load(source.url);

                    posts.addAll(parser.posts);
                }

            }));
        }


        try {
            if(!es.awaitTermination(30, TimeUnit.SECONDS)){
                Log.println(Log.DEBUG,"postloader","Posts loaded: " + posts.size());
                es.shutdownNow();
                Log.println(Log.DEBUG,"postloader","Shutting down threads");
            }
        } catch (InterruptedException e) {
            Log.println(Log.ERROR,"d",e.getMessage());
        }

        return posts;
    }

    public void parse(Document document) {
        if (document == null) return;

        if (WebUtils.isRss(document)) {
            RssParser rssParser = new RssParser();
            rssParser.parse(document);
            source = rssParser.getSource();
            posts = rssParser.getPosts();
        } else if (WebUtils.isAtom(document)) {
            AtomParser atomParser = new AtomParser();
            atomParser.parse(document);
            source = atomParser.getSource();
            posts = atomParser.getPosts();
        }
    }

    public static Date parseDate(String dateString) {
        List<DateTimeFormatter> formats = new ArrayList<>();
        formats.add(DateTimeFormatter.ofPattern("E, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH));
        formats.add(DateTimeFormatter.ofPattern("E, d MMM yyyy HH:mm:ss zzz", Locale.ENGLISH));
        formats.add(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
        formats.add(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH));

        for (DateTimeFormatter formatter : formats) {
            try {
                ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateString, formatter);
                return Date.from(zonedDateTime.toInstant());
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    public static String parsePattern(String raw, String attribute) {
        String regexPattern = attribute + "=\"(.*?)\"";
        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(raw);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "";
        }
    }

    public static String trim(String original, int maxLength) {
        if (original == null || original.length() <= maxLength) {
            return original;
        } else {
            return original.substring(0, maxLength - 3) + "...";
        }
    }

}

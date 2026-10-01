# Local subtitle files

Place downloaded `.srt` files in this directory for manual local parsing checks. Subtitle files are ignored by Git and must not be committed.

Start the app with `./gradlew bootRun`, then upload a file from the project root in another terminal:

```bash
curl -i -F 'file=@local-test-data/subtitles/example.srt' \
  http://localhost:8080/api/subtitles/upload
```

The response reports the parsed cue count. The application log reports file size, cue count, sequence range, timeline range, and parse duration without logging subtitle text.

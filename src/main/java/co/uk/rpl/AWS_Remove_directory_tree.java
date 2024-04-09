/*
 */
package co.uk.rpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import static java.lang.System.exit;
import static java.lang.System.in;
import static java.lang.System.out;
import net.sourceforge.argparse4j.ArgumentParsers;
import static net.sourceforge.argparse4j.impl.Arguments.booleanType;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;

/**
 *
 * @author philip
 */
public class AWS_Remove_directory_tree {

    private static Logger LOG = getLogger(AWS_Remove_directory_tree.class);

    public static void main(String[] argv) throws IOException {
        var argParse = ArgumentParsers.newFor("AWS remove directory tree").
                build().
                defaultHelp(true).
                description("delete a tree at the root of a bucket");
        argParse.addArgument("-b", "--bucket").
                setDefault("com.marmalademodel-dev.dev").
                help("Bucket name");
        argParse.addArgument("--allow").
                setDefault(false).
                type(booleanType()).
                help("If true allows prefixes to be "+
                     "specified that do not start with tmp or test");
        argParse.addArgument("key").nargs(1).help("Name of directory to delete");
        try {
            var args = argParse.parseArgs(argv);
            var creds = ProfileCredentialsProvider.builder().build();
            S3Client s3 = S3Client.builder().credentialsProvider(creds).build();
            var bucket = args.getString("bucket");
            var directory = (String)args.getList("key").get(0);
            if (!args.getBoolean("allow") &&
                !directory.startsWith("tmp") &&
                !directory.startsWith("test")) {
                out.println("This does not work for "+directory);
                out.println("It must start with \"tmp\" or \"test\"");
                exit(1);
            }

            var listReq = ListObjectVersionsRequest.builder().
                    bucket(bucket).prefix(directory).build();
            var listResp = s3.listObjectVersions(listReq);
            for (var c : listResp.versions()) {
                out.println("     " + c.key() + ": " + c.versionId());
            }
            try (var inr = new BufferedReader(new InputStreamReader(in))) {
                var running = true;
                while (running) {
                    out.print("Are these the files you wish to delete, (yes/no/quit/exit)?");
                    var read = inr.readLine();
                    if (read == null) {
                        throw new IOException();
                    }
                    switch (read.toLowerCase()) {
                        case "quit":
                        case "q":
                        case "exit":
                        case "e":
                            out.println("\nAborted");
                            exit(0);
                        case "yes":
                            running = false;
                            break;
                        default:
                    }
                }
                out.println("Deleting");
                var left = listResp.versions().size();
                for (var c : listResp.versions()) {
                    out.println(left-- + " left, Deleteing " + c.key() + ", version: " + c.versionId());
                    var delReq = DeleteObjectRequest.builder().bucket(bucket).
                            key(c.key()).versionId(c.versionId()).
                            build();
                    s3.deleteObject(delReq);
                }
            }
        } catch (ArgumentParserException e) {
            argParse.handleError(e);
            exit(1);

        }
    }
}

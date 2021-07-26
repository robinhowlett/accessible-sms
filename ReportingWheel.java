//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 11+
//DEPS info.picocli:picocli:4.2.0
//SOURCES Disease.java

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "ReportingWheel", mixinStandardHelpOptions = true, version = "SendSms 0.1",
        description = "An interactive CLI to simulate using a reporting wheel to generate 9-digit" +
                " codes to send via SMS")
class ReportingWheel implements Callable<Integer> {

    @CommandLine.Option(
            names = {"-d", "--day"},
            description = "The @|bold numeric day|@ of the month")
    private Integer dayOfMonth;

    @CommandLine.Option(
            names = {"-di", "--disease"},
            description = "The @|bold disease code|@ you are reporting")
    private String diseaseCode;

    @CommandLine.Option(
            names = {"-c", "--cases"},
            description = "The @|bold number of cases|@ to report for that day")
    private Integer numCases;

    public static void main(String... args) {
        int exitCode = new CommandLine(new ReportingWheel()).execute(args);
        System.exit(exitCode);
    }

    private void printlnAnsi(String msg) {
        System.out.println(CommandLine.Help.Ansi.AUTO.string(msg));
    }

    @Override
    public Integer call() {
        // primes < 32 since there are 1000 possible values for a 3-digit code, but a max "day"
        // value of 31 so 1000/31 = 32.25...
        // There are only 7 diseases and 20 valid "cases" numbers permitted, so we go with 31 as max
        int[] primes = new int[]{2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31};

        // we'll be randomly selecting 3 sequential primes, so the max 1st index is 3 from the end
        // this is randomized to simulate different wheels being used for different purposes
        // the point is the user doesn't need to do anything other than text the 9-digit number
        // (no secret key etc. needed)
        int random = new Random().nextInt(primes.length - 2);
        int daySeed = primes[random];
        int diseasesSeed = primes[random + 1];
        int casesSeed = primes[random + 2];

        // check if any command-line inputs are valid and if not, ask for them until they are
        // then simulate each data point being selected on a physical reporting wheel and
        // codified to a 3-digit, zero-padded number
        dayOfMonth = validateDayInputAskAgainIfNeeded(dayOfMonth);
        String codifiedDay = String.format(Locale.ROOT, "%03d", (dayOfMonth * daySeed)); // e.g. 003
        printlnAnsi("@|green day=" + dayOfMonth + ", code=" + codifiedDay + "|@" + System.lineSeparator());

        // same for the disease (check and codify)
        Disease disease = validateDiseaseInputAskAgainIfNeeded(diseaseCode);
        String codifiedDisease = String.format(Locale.ROOT, "%03d",
                ((disease.ordinal() + 1) * diseasesSeed)); // + 1 as we want to avoid
        // zero-indexes for generating codes
        printlnAnsi("@|green disease=" + disease.name() + ", code=" + codifiedDisease + "|@" + System.lineSeparator());

        // and also check the number of cases input (if any) and codify
        numCases = validateCasesInputAskAgainIfNeeded(numCases);
        String codifiedCases = String.format(Locale.ROOT, "%03d", (numCases * casesSeed));
        printlnAnsi("@|green cases=" + numCases + ", code=" + codifiedCases + "|@" + System.lineSeparator());

        // the message to send to the reporting service will be a 9-digit concatenation of the 3
        // codes
        String finalCodifiedMessage = codifiedDay.concat(codifiedDisease).concat(codifiedCases);

        try {
            printlnAnsi("Please text " + finalCodifiedMessage + " to +14158493243");
            printlnAnsi("@|green THANK YOU!|@");
        } catch (Exception e) {
            printlnAnsi("@|red FAILED|@");
            printlnAnsi("@|red " + e.getMessage() + "|@");
            return 1;
        }
        return 0;
    }

    private Integer validateDayInputAskAgainIfNeeded(Integer day) {
        while (day == null || (day < 1 || day > 31)) {
            printlnAnsi("@|red Missing/invalid day provided (1-31 required)|@");
            try {
                day = askForDayOfMonth();
            } catch (Exception e) {
                day = null;
            }
        }
        return day;
    }

    private Integer askForDayOfMonth() {
        String s = System.console().readLine("What day of the month is this report for?: ");
        return Integer.valueOf(s);
    }

    private Disease validateDiseaseInputAskAgainIfNeeded(String diseaseCode) {
        Disease disease = null;
        if (diseaseCode != null) {
            disease = Disease.lookupViaCode(diseaseCode.toLowerCase(Locale.ROOT));
        }
        while (disease == null) {
            printlnAnsi("@|red Missing/invalid disease code provided|@");
            diseaseCode = askForDiseaseCode();
            try {
                disease = Disease.lookupViaCode(diseaseCode.toLowerCase(Locale.ROOT));
            } catch (Exception e) {
                disease = null;
            }
        }
        return disease;
    }

    private String askForDiseaseCode() {
        String q =
                "Which disease are you reporting? (use the single-letter code only): ".concat(System.lineSeparator());
        for (Disease value : Disease.values()) {
            q = q.concat(value.getCode()).concat(": ").concat(value.name().concat(System.lineSeparator()));
        }
        return System.console().readLine(q).toLowerCase(Locale.ROOT);
    }

    private Integer validateCasesInputAskAgainIfNeeded(Integer cases) {
        // maybe "zero cases" would we a good thing to report but the demo wheels start at 1
        while (cases == null || (cases < 1 || cases > 20)) {
            printlnAnsi("@|red Missing/invalid cases metric provided (1-20 required)|@");
            try {
                cases = askForCases();
            } catch (Exception e) {
                cases = null;
            }
        }
        return cases;
    }

    private Integer askForCases() {
        String s = System.console().readLine("How many cases are you reporting?: ");
        return Integer.valueOf(s);
    }
}
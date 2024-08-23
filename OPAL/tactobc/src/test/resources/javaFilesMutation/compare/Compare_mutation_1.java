public class Compare_mutation_1 {

    public static void main(String[] args) {
        // Float comparison
        float f1 = 10.5f;
        float f2 = 20.5f;
        int floatComparisonResult = Float.compare(f1, f2);
        int floatComparisonResultTemp = floatComparisonResult; // Temporary variable
        System.out.println("Float comparison result: " + floatComparisonResult);

        // Double comparison
        double d1 = 100.123;
        double d2 = 100.456;
        int doubleComparisonResult = Double.compare(d1, d2);
        int doubleComparisonResultTemp = doubleComparisonResult; // Temporary variable
        System.out.println("Double comparison result: " + doubleComparisonResult);

        // Long comparison
        long l1 = 123456789L;
        long l2 = 987654321L;
        int longComparisonResult = Long.compare(l1, l2);
        int longComparisonResultTemp = longComparisonResult; // Temporary variable
        System.out.println("Long comparison result: " + longComparisonResult);

        // Combining results
        int combinedResult = floatComparisonResultTemp + doubleComparisonResultTemp + longComparisonResultTemp;
        System.out.println("Combined result: " + combinedResult);
    }
}
package org.Nysxl.Utils;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class StringBuildHandler {
    private String value;

    public StringBuildHandler(Builder builder) {
        this.value = builder.value;
    }

    public static class Builder {
        private String value;

        public Builder() {
            this.value = "";
        }

        public Builder setValue(String value) {
            this.value = value;
            return this;
        }

        public Builder append(String str) {
            this.value += str;
            return this;
        }

        public Builder splitString(String delimiter) {
            this.value = Arrays.asList(this.value.split(delimiter)).toString();
            return this;
        }

        public Builder removeCharacter(char character) {
            this.value = this.value.replace(Character.toString(character), "");
            return this;
        }

        public Builder replaceCharacter(char oldChar, char newChar) {
            this.value = this.value.replace(oldChar, newChar);
            return this;
        }

        public Builder removeWord(String word) {
            this.value = this.value.replace(word, "");
            return this;
        }

        public Builder toUpperCase() {
            this.value = this.value.toUpperCase();
            return this;
        }

        public Builder toLowerCase() {
            this.value = this.value.toLowerCase();
            return this;
        }

        public Builder formatLargeDoubleWithCommas(double value) {
            DecimalFormat formatter = new DecimalFormat("#,###.00");
            this.value = formatter.format(value);
            return this;
        }

        public Builder formatLargeDoubleWithM(double value) {
            String[] suffixes = {"", "K", "M", "B", "T", "Q"};
            double[] divisors = {1, 1_000, 1_000_000, 1_000_000_000, 1_000_000_000_000.0, 1_000_000_000_000_000.0};

            for (int i = divisors.length - 1; i >= 0; i--) {
                if (value >= divisors[i]) {
                    this.value = String.format("%.2f%s", value / divisors[i], suffixes[i]);
                    break;
                }
            }
            return this;
        }

        public Builder joinStrings(List<String> strings, String delimiter) {
            this.value = String.join(delimiter, strings);
            return this;
        }

        public Builder capitalizeWords() {
            this.value = Arrays.stream(this.value.split("\\s+"))
                               .map(word -> word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                               .collect(Collectors.joining(" "));
            return this;
        }

        public StringBuildHandler build() {
            return new StringBuildHandler(this);
        }
    }

    @Override
    public String toString() {
        return this.value;
    }
}
package proof;

import java.util.ArrayList;

class Solution {
    String rule;
    ArrayList<long[]> premises;

    Solution(String rule, ArrayList<long[]> premises) {
        this.rule = rule;
        this.premises = premises;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("rule:").append(rule).append("\n");
        for (long[] p : premises) {
            builder.append(p[0]).append(",").append(p[1]).append(",");
            builder.append(p[2]).append(",").append(p[3]).append("\n");
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object oObj) {
        if (!(oObj instanceof Solution))
            return false;
        Solution other = (Solution) oObj;
        if (other == this)
            return true;
        if (!other.rule.equals(this.rule))
            return false;

        if (other.premises.size() != this.premises.size())
            return false;

        //crosscheck
        for (long[] p : this.premises) {
            boolean exists = false;
            for (long[] o : other.premises) {
                if (o[0] == p[0] && o[1] == p[1] && o[2] == p[2] && o[3] == p[3] && o[4] == p[4]) {
                    exists = true;
                    break;
                }
            }
            if (!exists)
                return false;
        }
        return true;
    }
}

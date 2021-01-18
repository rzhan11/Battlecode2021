package template;

import battlecode.common.*;

import static template.Debug.*;
import static template.Robot.*;
import static template.EnlightenmentCenter.*;

public enum Role {
    MUCK_ROLE(150, 1.0, "MUCK"),
    DEFENSE_POLI_ROLE(150, 1.0, "DEF_POLI"),
    ATTACK_POLI_ROLE(150, 1.0, "ATK_POLI"),
    EXPLORE_POLI_ROLE(50, 1.0, "EXP_POLI"),
    SLAN_ROLE(100, 1.0, "SLAN");

    final public String name;

    final public int max;
    public int count;
    public int[] ids;
    public int deleteIndex;

    public double ratio;
    public double score;

    Role(int max, double ratio, String name) {
        this.name = name;

        this.max = max;
        this.count = 0;
        this.deleteIndex = 0;

        this.ratio = ratio;
        this.score = 0.0;
    }

    @Override
    public String toString() {
        return this.name;
    }

    final public static Role[] ROLE_ORDER = new Role[] {
            MUCK_ROLE,
            DEFENSE_POLI_ROLE,
            ATTACK_POLI_ROLE,
            EXPLORE_POLI_ROLE,
            SLAN_ROLE
    };

    // variables

    public static int[] mySlanSpawnRounds;
    public static int[] mySlandererEarns; // amt of influence genned per turn

    // total amount of influence in slanderers
    public static int mySlanInfluence = 0;
    // amount of influence generated by slanderers per turn
    public static int mySlanTotalEarn = 0;
    // total amount of influence that can be generated by slanderers
    public static int mySlanTotalValue = 0;

    public static int myUnitCount = 0; // myMuckrakersCount + myPoliticianCount + mySlandererCount

    public static void initRoles() {
        for (Role role: ROLE_ORDER) {
            role.ids = new int[role.max];
        }

        mySlanSpawnRounds = new int[SLAN_ROLE.max];
        mySlandererEarns = new int[SLAN_ROLE.max];
    }

    public static int getAllRolesCount() {
        int total = 0;
        for (int i = ROLE_ORDER.length; --i >= 0;) {
            total += ROLE_ORDER[i].count;
        }
        return total;
    }


    public static void updateRoleCounts() throws GameActionException {
        // delete dead muckrakers
        checkDeadIDs(MUCK_ROLE);
        checkDeadIDs(DEFENSE_POLI_ROLE);
        checkDeadIDs(ATTACK_POLI_ROLE);
        checkDeadIDs(EXPLORE_POLI_ROLE);
        // do not checkDeadIDs(SLAN_ROLE)

        // checking dead ids of slans
        int mySlanCount = SLAN_ROLE.count;
        int[] mySlans = SLAN_ROLE.ids;

        mySlanInfluence = 0;
        mySlanTotalEarn = 0;
        mySlanTotalValue = 0;
        for (int i = mySlanCount; --i >= 0;) {
            if (!rc.canGetFlag(mySlans[i])) {
                // delete dead slanderers
                mySlanCount--;
                mySlans[i] = mySlans[mySlanCount];
                mySlanSpawnRounds[i] = mySlanSpawnRounds[mySlanCount];
                mySlandererEarns[i] = mySlandererEarns[mySlanCount];
            } else if (roundNum - mySlanSpawnRounds[i] >= GameConstants.CAMOUFLAGE_NUM_ROUNDS) {
                // checks if slanderers have turned to politicians
                // if so, delete from slanderer array and add to politician array
                addRoleBasic(ATTACK_POLI_ROLE, mySlans[i]);
                mySlanCount--;
                mySlans[i] = mySlans[mySlanCount];
                mySlanSpawnRounds[i] = mySlanSpawnRounds[mySlanCount];
                mySlandererEarns[i] = mySlandererEarns[mySlanCount];
            } else {
                // count value of slanderers
                int earn = mySlandererEarns[i];
                int earnRounds = GameConstants.EMBEZZLE_NUM_ROUNDS - roundNum + mySlanSpawnRounds[i];
                mySlanInfluence += SLANDERER_COSTS[earn];
                if (earnRounds > 0) {
                    mySlanTotalEarn += earn;
                    mySlanTotalValue += earn * earnRounds;
                }
            }
        }
        SLAN_ROLE.count = mySlanCount;

//        log("Slan Influence " + mySlanInfluence);
//        log("Slan Earn " + mySlanTotalEarn);
//        log("Slan Value " + mySlanTotalValue);

        myUnitCount = getAllRolesCount();
        logi("Roles: " + MUCK_ROLE.count + " "
                + DEFENSE_POLI_ROLE.count + " " + ATTACK_POLI_ROLE.count + " " + EXPLORE_POLI_ROLE.count
                + " " + SLAN_ROLE.count);
    }

    /*
    Only to be used for non-SLANDERERS
     */
    public static void checkDeadIDs(Role role) {
        switch (role) {
            case SLAN_ROLE:
                logi("WARNING: Bad input role " + role);
                return;
            default: break;
        }

        int[] myIDs = role.ids;
        int count = role.count;
        // delete dead roles
        for (int i = count; --i >= 0;) {
            if (!rc.canGetFlag(myIDs[i])) {
                count--;
                myIDs[i] = myIDs[count];
            }
        }
        role.count = count;
    }

    public static void addRole(Role role, RobotInfo ri) {
        switch(role) {
            case MUCK_ROLE:
            case DEFENSE_POLI_ROLE:
            case ATTACK_POLI_ROLE:
            case EXPLORE_POLI_ROLE:
                addRoleBasic(role, ri.ID);
                return;
            case SLAN_ROLE:
                addRoleSlan(ri.ID, ri.influence);
                return;
            default:
                logi("WARNING: 'addRole' received unknown role " + role);
                return;
        }

    }

    public static void addRoleBasic(Role role, int id) {
        if (role.count < role.max) {
            role.ids[role.count] = id;
            role.count++;
        } else {
            role.ids[role.deleteIndex] = id;
            role.deleteIndex = (role.deleteIndex + 1) % role.max;
        }
    }

    public static void addRoleSlan(int id, int influence) {
        Role role = SLAN_ROLE;
        int[] myIDs = role.ids;
        int count = role.count;
        int max = role.max;
        if (count < max) {
            myIDs[count] = id;
            mySlanSpawnRounds[count] = roundNum + 1;
            mySlandererEarns[count] = HardCode.getPassiveInfluence(influence);
            // UPDATE ROLE VARIABLE
            role.count++;
        } else {
            int deleteIndex = role.deleteIndex;
            myIDs[deleteIndex] = id;
            mySlanSpawnRounds[deleteIndex] = roundNum + 1;
            mySlandererEarns[deleteIndex] = HardCode.getPassiveInfluence(influence);
            // UPDATE ROLE VARIABLE
            role.deleteIndex = (role.deleteIndex + 1) % max;
        }
    }

    public static void updateRoleScores() {
        for (Role role: ROLE_ORDER) {
            role.score = role.count / role.ratio;
        }

        // cap spawn count
        if (MUCK_ROLE.count >= MUCK_CAP) MUCK_ROLE.score = P_INF;
        if (rc.getInfluence() > 0.1 * GameConstants.ROBOT_INFLUENCE_LIMIT) MUCK_ROLE.score = P_INF;

        if (SLAN_ROLE.count >= SLAN_CAP) SLAN_ROLE.score = P_INF;
        if (rc.getInfluence() > 1e5) SLAN_ROLE.score = P_INF;



        log("BUILD SCORES");
        for (Role role: ROLE_ORDER) {
            role.score = role.count / role.ratio;
            tlog(role.toString() + ": " + role.score);
        }
    }
}

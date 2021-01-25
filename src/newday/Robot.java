package newday;

import battlecode.common.*;

import static newday.Comms.*;
import static newday.Debug.*;
import static newday.HQTracker.*;
import static newday.Map.*;
import static newday.Nav.*;
import static newday.Utils.*;

public abstract class Robot extends Constants {

    /*
    True constants
     */
    final public static Direction[] DIRS = {Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST}; // 8 directions
    final public static Direction[] ALL_DIRS = {Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST, Direction.CENTER}; // includes center
    final public static Direction[] CARD_DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}; // four cardinal directions
    final public static Direction[] DIAG_DIRS = {Direction.NORTHEAST, Direction.SOUTHEAST, Direction.SOUTHWEST, Direction.NORTHWEST}; // four diagonals

    final public static Team neutral = Team.NEUTRAL;

    final public static int MAX_HQ_COUNT = 12;

    /*
    Variables that will never change (once set)
    */
    public static RobotController rc;

    public static int myID;
    public static RobotType myType;
    public static RobotInfo me;

    public static int spawnRound; // the first round this robot was called through RobotPlayer.java
    public static MapLocation spawnLoc;

    public static int myActionRadius;
    public static int mySensorRadius;
    public static int myDetectionRadius;

    public static Team us;
    public static Team them;

    public static int maxSensedUnits;

    public static void init(RobotController theRC) throws GameActionException {
        rc = theRC;

        myID = rc.getID();
        myType = rc.getType();
        me = rc.senseRobot(myID);

        spawnRound = rc.getRoundNum();
        spawnLoc = rc.getLocation();

        myActionRadius = myType.actionRadiusSquared;
        mySensorRadius = myType.sensorRadiusSquared;
        myDetectionRadius = myType.detectionRadiusSquared;

        us = rc.getTeam();
        them = us.opponent();

        Utils.RANDOM_SEED = myID;
        Nav.wanderLeft = randBoolean();

        // after simple updates, since log needs myType

        Debug.clearBuffer();
        log("INIT ROBOT");

        Comms.initBaseCoords(rc.getLocation());
        CommManager.initQueues();

        HardCode.initHardCode();
        switch(myType) {
            case ENLIGHTENMENT_CENTER:
                maxSensedUnits = 128;
                break;
            case MUCKRAKER:
                maxSensedUnits = 96;
                break;
            case POLITICIAN:
                maxSensedUnits = 80;
                break;
            case SLANDERER:
                maxSensedUnits = 68;
                break;
        }

        enemyMuckrakers = new RobotInfo[maxSensedUnits];
        enemyPoliticians = new RobotInfo[maxSensedUnits];
        enemySlanderers = new RobotInfo[maxSensedUnits];

        for (int i = 0; i < hqLocs.length; i++) {
            hqIDs[i] = -1;
            hqSurroundRounds[i] = DEFAULT_SURROUND;
        }

        Debug.printBuffer();
    }

    /*
    Variables that (may) change each turn
     */

    public static MapLocation here;
    public static int roundNum;
    public static int age;
    public static boolean wasSlanderer = false;

    public static double myPassability;
    public static int myInfluence;
    public static int myConviction;

    public static double curAllyBuff;
    public static double curEnemyBuff;

    public static RobotInfo[] sensedRobots;
    public static RobotInfo[] sensedAllies;
    public static RobotInfo[] sensedEnemies;
    public static RobotInfo[] sensedNeutrals;

    public static RobotInfo[] enemyMuckrakers;
    public static RobotInfo[] enemyPoliticians;
    public static RobotInfo[] enemySlanderers;
    public static int enemyMuckrakerCount;
    public static int enemyPoliticianCount;
    public static int enemySlandererCount;
    public static RobotInfo[] sensedEnemyHQs = new RobotInfo[MAX_HQ_COUNT];
    public static int sensedEnemyHQCount;

    public static RobotInfo[] adjAllies;

    public static boolean[] isDirMoveable = new boolean[8];
    public static boolean isStuck;

    public static int myMaster = -1;
    public static MapLocation myMasterLoc = null;

    public static MapLocation alertEnemyMuckraker;
    public static int alertEnemyMuckrakerRound;


    public static void updateTurnInfo() throws GameActionException {

        // todo TESTING PURPOSES ONLY
//        if (roundNum >= 400) {
//            log("RESIGNING");
//            rc.resign();
//        }

        CommManager.resetFlag();
        Comms.resetPrevEcho();

        // independent
        updateBasicInfo();
        Comms.SKIP_WRITE = (myType != RobotType.ENLIGHTENMENT_CENTER && age == 0 && roundNum > 10);

        // independent
        sortEnemyTypes();
        updateIsDirMoveable();

        updateMapBounds();

        // after updateMapBounds & symmetry stuff
        updateKnownHQs();

        // after updateKnownHQs
        // after updateSymmetryByHQ
        if (myType != RobotType.SLANDERER) {
            updateSymmetryByHQ();
            updateSymHQLocs(); // this finds hq locs based on symmetry
        }

        // after updateKnownHQs
        updateMaster();

        // after updateMaster
        readMasterComms();

        // after readMasterComms, updateKnownHQs
        readHQComms();

        // alertLoc
        updateAlerts();

        if (myType != RobotType.SLANDERER) {
            int curByte = Clock.getBytecodesLeft();
            // map info
            log("MAP X " + new MapLocation(XMIN, XMAX));
            log("MAP Y " + new MapLocation(YMIN, YMAX));
            printSymmetry();

            // hq info
            log("HQs: " + knownHQCount);
            for (int i = 0; i < knownHQCount; i++) {
                String teamName;
                if (hqTeams[i] == Team.NEUTRAL) {
                    teamName = "N";
                } else if (hqTeams[i] == null) {
                    teamName = "U"; // unknown
                } else {
                    teamName = hqTeams[i].toString();
                }
//                tlog(hqLocs[i] + " " + hqIDs[i] + " " + teamName + " " + hqSurroundRounds[i] + " " + hqReportSurroundRounds[i] + " " + hqIgnoreRounds[i]);
                tlog(hqLocs[i] + " " + hqIDs[i] + " " + teamName + " " + hqInfluence[i]);
            }
            log("Print cost " + (curByte - Clock.getBytecodesLeft()));
        }

        printBuffer();
    }

    /*
    These updates should be cheap and independent of other updates
     */
    public static void updateBasicInfo() throws GameActionException {
        here = rc.getLocation();
        roundNum = rc.getRoundNum();
        age = roundNum - spawnRound;

        myPassability = rc.sensePassability(here);
        myInfluence = rc.getInfluence();
        myConviction = rc.getConviction();

        curAllyBuff = rc.getEmpowerFactor(us, 0);
        curEnemyBuff = rc.getEmpowerFactor(them, 0);

        sensedRobots = rc.senseNearbyRobots();
        sensedAllies = rc.senseNearbyRobots(-1, us);
        sensedEnemies = rc.senseNearbyRobots(-1, them);
        sensedNeutrals = rc.senseNearbyRobots(-1, neutral);

        adjAllies = rc.senseNearbyRobots(2, us);

        // print basic info
        printMyInfo();
        printBuffer();
    }

    public static void sortEnemyTypes() throws GameActionException {
        enemyMuckrakerCount = 0;
        enemyPoliticianCount = 0;
        enemySlandererCount = 0;
        sensedEnemyHQCount = 0;

        for (int i = sensedEnemies.length; --i >= 0;) {
//        for (RobotInfo ri: sensedEnemies) {
            RobotInfo ri = sensedEnemies[i];
            switch(ri.type) {
                case MUCKRAKER:
                    enemyMuckrakers[enemyMuckrakerCount++] = ri;
                    break;

                case POLITICIAN:
                    enemyPoliticians[enemyPoliticianCount++] = ri;
                    break;

                case SLANDERER:
                    enemySlanderers[enemySlandererCount++] = ri;
                    break;

                case ENLIGHTENMENT_CENTER:
                    sensedEnemyHQs[sensedEnemyHQCount++] = ri;
                    break;

                default:
                    break;
            }
        }
    }

    public static void updateMaster() throws GameActionException {
        if (myType == RobotType.ENLIGHTENMENT_CENTER) {
            return;
        }

        // check if master is dead
        if (!rc.canGetFlag(myMaster)) {
            myMaster = -1;
            myMasterLoc = null;
        }

        // if no master, try to find new master
        if (myMaster < 0) {
            // updates myMaster and myMasterLoc
            int bestDist = P_INF;
            for (int i = knownHQCount; --i >= 0;) {
                int id = hqIDs[i];
                // checks if this is an ally hq with known id
                if (id > 0 && hqTeams[i] == us) {
                    int dist = here.distanceSquaredTo(hqLocs[i]);
                    if (dist < bestDist) {
                        myMaster = id;
                        myMasterLoc = hqLocs[i];
                        bestDist = dist;
                    }
                }
            }
            // if found a new master, broadcast this master
            if (myMaster > 0) {
                writeBroadcastMyMaster(true);
            }
        }

        log("Master: " + myMaster + "@" + myMasterLoc);
    }

    public static MapLocation getCenterLoc() {
        if (myMasterLoc != null) return myMasterLoc;
        return spawnLoc;
    }

    public static void readMasterComms() throws GameActionException {
        if (myType == RobotType.ENLIGHTENMENT_CENTER) {
            return;
        }
        if (myMaster > 0) {
            Comms.readMessage(myMaster);
        }
    }

    public static void readHQComms() throws GameActionException {
        // should be safe, no need for "rc.canGetFlag"
        for (int i = knownHQCount; --i >= 0;) {
            int id = hqIDs[i];
            if (hqTeams[i] == us && id > 0) {
                if (id != myID && id != myMaster) {
                    Comms.readMessage(id);
                }
            }
        }
        for (int i = extraAllyHQCount; --i >= 0;) {
            int id = extraAllyHQs[i];
            if (id != myID && id != myMaster) {
                Comms.readMessage(id);
            }
        }
    }

    public static int lastWriteEnemyMuckrakerRound = -100;
    final public static int WRITE_ENEMY_MUCKRAKER_FREQ = 3;

    public static void updateAlerts() throws GameActionException {
        // check if reset current alert loc
        if (alertEnemyMuckraker != null) {
            if (roundNum - alertEnemyMuckrakerRound > 2 * WRITE_ENEMY_MUCKRAKER_FREQ) {
                log("Resetting alertEnemyMuckraker");
                alertEnemyMuckraker = null;
                alertEnemyMuckrakerRound = -100;
            }
        }


        // report enemy muckrakers
        MapLocation alertLoc;
        if (enemyMuckrakerCount > 10) {
            alertLoc = enemyMuckrakers[randInt(enemyMuckrakerCount)].location;
        } else {
            alertLoc = null;
            int bestDist = P_INF;
            for (int i = enemyMuckrakerCount; --i >= 0;) {
                RobotInfo ri = enemyMuckrakers[i];
                int dist = here.distanceSquaredTo(ri.location);
                if (dist < bestDist) {
                    alertLoc = ri.location;
                    bestDist = dist;
                }
            }
        }


        log("alertEnemyMuckraker " + alertEnemyMuckraker + " " + alertEnemyMuckrakerRound);
        log("Alert " + alertLoc);
        if (alertLoc != null) {
            if (roundNum - lastWriteEnemyMuckrakerRound > WRITE_ENEMY_MUCKRAKER_FREQ) {
                writeReportEnemyMuckraker(alertLoc);
            }
            // todo consider uncommenting this
//            addAlertLoc(alertLoc);
        }
    }

    public static void addAlertLoc(MapLocation loc) throws GameActionException {
        int prevDist = (alertEnemyMuckraker != null) ? here.distanceSquaredTo(alertEnemyMuckraker) : P_INF;
        int dist = here.distanceSquaredTo(loc);
        if (dist < prevDist) {
            alertEnemyMuckraker = loc;
            alertEnemyMuckrakerRound = roundNum;
        }
    }

    public static void readUnitComms() throws GameActionException {
        if (myType == RobotType.ENLIGHTENMENT_CENTER) {
            return;
        }
        log("Reading Unit Comms");

        // must reset here
        sensedAllies = rc.senseNearbyRobots(-1, us);
        if (sensedAllies.length == 0) {
            return;
        }

        int count = sensedAllies.length;

//        Debug.SILENCE_LOGS = true;
        for (int i = sensedAllies.length; --i >= 0;) {
            if (Clock.getBytecodesLeft() > 1500 && roundNum == rc.getRoundNum()) {
                Comms.readMessage(sensedAllies[i].ID);
            } else {
                count = sensedAllies.length - 1 - i;
                break;
            }
        }
//        Debug.SILENCE_LOGS = false;

        tlog("Processed " + count + "/" + sensedAllies.length + " messages");
        printBuffer();
    }

    public static boolean onlyDefaultExplore;
    public static boolean rotateLeftExplore;
    public static boolean tripleRotateExplore;
    public static int lastExploreTaskChangeRound = -1;

    // master
    public static Direction masterTaskDir;
    // bounds
    public static Direction boundsTaskDir;
    // symmetry
    public static MapLocation symmetryTaskLoc;
    public static Symmetry symmetryTaskType;
    // default explore
    public static Direction defaultExploreTaskDir;

    public static int numStuckTaskRounds;



    /*
    Exploration code
     */

    public static void initExploreTask() throws GameActionException {
        initExploreTask(random() < 0.5);
    }

    public static void initExploreTask(boolean onlyDefault) throws GameActionException {
        // master
        if (myMaster > 0) {
            int status = Comms.getStatusFromFlag(rc.getFlag(myMaster));
            masterTaskDir = DIRS[status % 8];
            numStuckTaskRounds = 0;
        }
        // bounds
        // symmetry

        // default

        onlyDefaultExplore = onlyDefault;

        defaultExploreTaskDir = getRandomDirCenter(); // randomize exploreDir
        rotateLeftExplore = randBoolean(); // randomize whether we turn left or right
        tripleRotateExplore = randBoolean(); // randomize whether we do a single rotate or a triple rotate
    }

    public static void updateExploreTask() {
        if (numStuckTaskRounds > 5) {
            // reset all tasks
            masterTaskDir = null;
            boundsTaskDir = null;
            symmetryTaskLoc = null;
            Direction newDir = getRandomDirCenter();
            while (newDir == defaultExploreTaskDir) {
                 newDir = getRandomDirCenter();
            }
            defaultExploreTaskDir = newDir;
            numStuckTaskRounds = 0;
        }

        // update if masterTaskDir is done
        if (masterTaskDir != null) {
            MapLocation senseLoc = getFarthestLoc(spawnLoc, masterTaskDir);
            if (rc.canSenseLocation(senseLoc) || roundNum - lastExploreTaskChangeRound > 100) {
                masterTaskDir = null;
                // do not return, we need new task
            } else { // keeping this task
                return;
            }
        }


        if (!onlyDefaultExplore) {
            // check if boundsTask needs to be changed
            if (boundsTaskDir != null) {
                // check if we still need to go to bounds task dir
                int dx = boundsTaskDir.dx;
                int dy = boundsTaskDir.dy;
                log("orig " + dx + " " + dy + " " +boundsTaskDir);
                if (dx == -1 && XMIN != -1) dx = 0;
                if (dx == 1 && XMAX != -1) dx = 0;
                if (dy == -1 && YMIN != -1) dy = 0;
                if (dy == 1 && YMAX != -1) dy = 0;

                if (dx == 0 && dy == 0) {
                    log("Reset boundsTask");
                    boundsTaskDir = null;
                    // do not return, we need new task
                } else { // check if direction has changed
                    Direction newDir = getDir(dx, dy);
                    if (!newDir.equals(boundsTaskDir)) {
                        log("Adjusted boundsTaskDir " + newDir);
                        boundsTaskDir = newDir;
                        lastExploreTaskChangeRound = roundNum;
                        numStuckTaskRounds = 0;
                    }
                    return;
                }
            }
            // assign new boundsTask if needed
            if (boundsTaskDir == null && !isMapKnown()) {
                log("Getting new boundsTask");
                boundsTaskDir = getNewBoundsTaskDir();
                lastExploreTaskChangeRound = roundNum;
                numStuckTaskRounds = 0;
                return;
            }

            // check if we need to reset symmetryTask
            if (symmetryTaskLoc != null) {
                // if we know this symmetry is invalid
                if ((symmetryTaskType == Symmetry.H && notHSymmetry)
                        || (symmetryTaskType == Symmetry.V && notVSymmetry)
                        || (symmetryTaskType == Symmetry.R && notRSymmetry)) {
                    symmetryTaskLoc = null;
                    symmetryTaskType = null;
                    // do not return, we need new task
                } else if (rc.canSenseLocation(symmetryTaskLoc) // if we can sense this hq loc
                        || inArray(hqLocs, symmetryTaskLoc, knownHQCount) // or if this hq has been found
                        || roundNum - lastExploreTaskChangeRound > 100) {
                    symmetryTaskLoc = null;
                    symmetryTaskType = null;
                    // do not return, we need new task
                } else { // keeping this task
                    return;
                }
            }
            if (symmetryTaskLoc == null) {
                if (symHQCount > 0) {
                    symmetryTaskLoc = symHQLocs[0];
                    symmetryTaskType = symHQType[0];
                    lastExploreTaskChangeRound = roundNum;
                    numStuckTaskRounds = 0;
                    return;
                }
            }
        }


        // if we get here, we need to get a default task

        MapLocation mapCenter = new MapLocation(isMapXKnown() ? (XMIN + XMAX) / 2 : spawnLoc.x,
                isMapYKnown() ? (YMIN + YMAX) / 2 : spawnLoc.y);
        MapLocation senseLoc = convertToKnownBounds(addDir(mapCenter, defaultExploreTaskDir, MAX_MAP_SIZE));

        // loop thru ally hqs and check
        boolean hasCornerAllyHQ = false;
        for (int i = knownHQCount; --i >= 0;) {
            if (hqTeams[i] == us && rc.canSenseLocation(hqLocs[i]) && hqLocs[i].isWithinDistanceSquared(senseLoc, 40)) {
                hasCornerAllyHQ = true;
                break;
            }
        }

        if (rc.canSenseLocation(senseLoc)
                || roundNum - lastExploreTaskChangeRound > 150
                || hasCornerAllyHQ) {
            lastExploreTaskChangeRound = roundNum;
            numStuckTaskRounds = 0;

            if (defaultExploreTaskDir == Direction.CENTER) {
                defaultExploreTaskDir = getRandomDir();
            } else {
//                Direction oldDir = defaultExploreTaskDir;
//                do {
//                    defaultExploreTaskDir = getRandomDirCenter();
//                } while (oldDir == defaultExploreTaskDir);
                if (random() < 0.25) { // 1/4 chance that we pick center
                    log("Exploring center");
                    defaultExploreTaskDir = Direction.CENTER;
                } else {
                    if (tripleRotateExplore) { // choose new exploreDir, either rotate 1 or 3
                        log("Triple rotating explore");
                        defaultExploreTaskDir = rotateLeftExplore ? defaultExploreTaskDir.rotateLeft().rotateLeft().rotateLeft() :
                                defaultExploreTaskDir.rotateRight().rotateRight().rotateRight();
                    } else {
                        log("Single rotating explore");
                        defaultExploreTaskDir = rotateLeftExplore ? defaultExploreTaskDir.rotateLeft() : defaultExploreTaskDir.rotateRight();
                    }
                }
            }

        }
        log("Exploring " + defaultExploreTaskDir);
    }

    /*
    Does the next "exploration" task
    Priority: Master, Map Bounds, Map Symmetry (by HQs), Default (go to directions)
     */
    public static void explore() throws GameActionException {
        numStuckTaskRounds++;

        // do master task
        if (masterTaskDir != null) {
            // purposely uses spawnLoc
            log("Master task " + masterTaskDir);
            MapLocation senseLoc = getFarthestLoc(spawnLoc, masterTaskDir);
            MapLocation navLoc = getExploreNavLoc(senseLoc);
            drawLine(here, navLoc, WHITE);
            drawDot(senseLoc, WHITE);
            // use fuzzy for this one
            Direction dir = smartMove(navLoc);
            if (dir != null) {
                numStuckTaskRounds = 0;
            }
//            moveLog(navLoc);
            return;
        }

        MapLocation mapCenter = new MapLocation(isMapXKnown() ? (XMIN + XMAX) / 2 : spawnLoc.x,
                isMapYKnown() ? (YMIN + YMAX) / 2 : spawnLoc.y);


        // go to dir
        if (boundsTaskDir != null) {
            log("Bounds task " + boundsTaskDir);
            MapLocation senseLoc = convertToKnownBounds(addDir(mapCenter, boundsTaskDir, MAX_MAP_SIZE));
            MapLocation navLoc = getExploreNavLoc(senseLoc);
            drawLine(here, navLoc, GRAY);
            drawDot(senseLoc, GRAY);
            Direction dir = smartMove(navLoc);
            if (dir != null) {
                numStuckTaskRounds = 0;
            }
//            moveLog(navLoc);
            return;
        }


        // check hq symmetry
        if (symmetryTaskLoc != null) {
            log("Symmetry task " + symmetryTaskLoc + " " + symmetryTaskType);
            drawLine(here, symmetryTaskLoc, BLACK);
            Direction dir = smartMove(symmetryTaskLoc);
            if (dir != null) {
                numStuckTaskRounds = 0;
            }
//            moveLog(symmetryTaskLoc);
            return;
        }




        // do the default explore task
        {
            log("Default explore task " + defaultExploreTaskDir);
            MapLocation senseLoc = convertToKnownBounds(addDir(mapCenter, defaultExploreTaskDir, MAX_MAP_SIZE));
            MapLocation navLoc = getExploreNavLoc(senseLoc);
            drawLine(here, navLoc, BROWN);
            drawDot(senseLoc, BROWN);
            Direction dir = smartMove(navLoc);
            if (dir != null) {
                numStuckTaskRounds = 0;
            }
            return;
        }
    }

    public static int getDamage(int conviction, double buff) {
        return (int) Math.max(0, (conviction - GameConstants.EMPOWER_TAX) * buff);
    }

    /*
    Run at the end of each turn
    Checks if we exceeded the bytecode limit
     */
    public static void endTurn() throws GameActionException {
        // reads unit comms at destination
        readUnitComms();

        log("Using " + (CommManager.useRepeatQueue ? "REPEAT Queue" : "NORMAL Queue"));
        CommManager.printMessageQueue();
        CommManager.printRepeatQueue();
        CommManager.updateMessageCount();

        // check if we went over the bytecode limit
        int endTurn = rc.getRoundNum();
        if (roundNum != endTurn) {
            printMyInfo();
            logi("BYTECODE LIMIT EXCEEDED");
            int bytecodeOver = Clock.getBytecodeNum();
            int turns = endTurn - roundNum;
            tlogi("Overused bytecode: " + (bytecodeOver + (turns - 1) * myType.bytecodeLimit));
            tlogi("Skipped turns: " + turns);
        }

        printBuffer();
        // print endTurn information
        if (!NO_TURN_LOGS) {
            logline();
            log("END TURN");
            tlog("Flag Status: " + CommManager.getStatus());
            tlog(CommManager.getMessage().toString());
            tlog("Bytecode left: " + Clock.getBytecodesLeft());
            logline();
            printBuffer();
        }
        Clock.yield();
    }

    final public static int cooldownRounding = 100;
    final public static double cooldownRoundingDouble = (double) cooldownRounding;

    public static void printMyInfo () {
        if (NO_TURN_LOGS) return;
        logline();
        log(myType + " " + myID);
        log("Loc: " + here + ". R: " + roundNum);
        log("Conv/Infl: " + rc.getConviction() + "/" + rc.getInfluence());
        log("Cooldown: " + (int) (rc.getCooldownTurns() * cooldownRounding) / cooldownRoundingDouble);
        logline();
    }
}

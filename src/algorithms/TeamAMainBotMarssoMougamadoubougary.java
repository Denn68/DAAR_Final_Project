/* ******************************************************
 * Team A Main Bot - Commander
 * - IDLE BEHAVIOR: Random Patrol when no messages received.
 * ******************************************************/
package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;
import java.util.ArrayList;

public class TeamAMainBotMarssoMougamadoubougary extends Brain {
    
    // States
    private static final int STATE_SETUP = 0;
    private static final int STATE_DEPLOY = 1;
    private static final int STATE_PATROL = 2; // New Random State
    private static final int STATE_HUNTING = 3;
    private static final int STATE_COMBAT = 4;
    private static final int STATE_UNSTUCK = 5;

    // Lanes
    private static final int LANE_UNKNOWN = 0;
    private static final int LANE_TOP = 1;
    private static final int LANE_MID = 2;
    private static final int LANE_BOT = 3;

    // Variables
    private int state;
    private int previousState;
    private int lane;
    private int fireCooldown;
    private int stuckTimer;
    private int unstuckActionTimer;
    private boolean unstuckTurnRight;
    
    private double targetX, targetY; 
    private double myEstimatedX, myEstimatedY;
    private int huntTimer;
    
    // Patrol Variables
    private int patrolTimer;
    private double patrolAngle;
    
    // Constants
    private static final int FIRE_LATENCY = 20;
    private static final double ANGLE_60 = Math.PI / 3.0;
    private static final int STUCK_THRESHOLD = 10;
    private static final int UNSTUCK_DURATION = 25; 
    private static final int DEPLOY_TIME = 100; // Ticks to stay in strict formation

    public TeamAMainBotMarssoMougamadoubougary() {
        super();
    }

    public void activate() {
        state = STATE_SETUP;
        lane = LANE_UNKNOWN;
        fireCooldown = 0;
        stuckTimer = 0;
        patrolTimer = 0;
        myEstimatedX = 200;
        myEstimatedY = 1000;
        sendLogMessage("Main Bot Activated.");
    }

    public void step() {
        if (fireCooldown > 0) fireCooldown--;
        
        // 1. RECEIVE INTEL
        processMessages();

        // 2. RADAR & SHARE
        ArrayList<IRadarResult> radar = detectRadar();
        IRadarResult localEnemy = getBestTarget(radar);
        if (localEnemy != null) {
            broadcastTargetInfo(localEnemy);
        }

        // 3. TEAM AVOIDANCE
        IFrontSensorResult front = detectFront();
        if (front.getObjectType() == IFrontSensorResult.Types.TeamMainBot || 
            front.getObjectType() == IFrontSensorResult.Types.TeamSecondaryBot) {
            moveBack();
            return;
        }

        // 4. STUCK DETECTION
        if (isBlocked()) {
            stuckTimer++;
            if (stuckTimer > STUCK_THRESHOLD && state != STATE_UNSTUCK) {
                previousState = state;
                state = STATE_UNSTUCK;
                unstuckActionTimer = UNSTUCK_DURATION;
                unstuckTurnRight = Math.random() > 0.5; 
            }
        } else {
            stuckTimer = 0;
        }

        // 5. STATE MACHINE
        if (state == STATE_UNSTUCK) {
            handleUnstuck();
            return;
        }

        if (localEnemy != null) {
            state = STATE_COMBAT;
            handleCombat(localEnemy);
        } else {
            if (state == STATE_COMBAT) state = STATE_PATROL; // Fight over? Go back to patrol

            switch (state) {
                case STATE_SETUP:
                    determineLane(radar);
                    if (lane != LANE_UNKNOWN) state = STATE_DEPLOY;
                    break;
                    
                case STATE_DEPLOY:
                    // Strict formation for a short time, then Random Patrol
                    if (patrolTimer < DEPLOY_TIME) {
                        patrolTimer++;
                        handleDeployment();
                    } else {
                        state = STATE_PATROL;
                        patrolTimer = 0;
                    }
                    break;
                    
                case STATE_PATROL:
                    handlePatrol(); // Random movement
                    break;
                    
                case STATE_HUNTING:
                    handleHunting();
                    break;
            }
        }
        
        // Odometry
        if (!isBlocked() && state != STATE_UNSTUCK) {
            myEstimatedX += Parameters.teamAMainBotSpeed * Math.cos(getHeading());
            myEstimatedY += Parameters.teamAMainBotSpeed * Math.sin(getHeading());
        }
    }

    // --- BEHAVIORS ---

    private void handleCombat(IRadarResult enemy) {
        double enemyDir = enemy.getObjectDirection();
        turnTo(enemyDir);
        
        // Shoot if ready
        if (fireCooldown <= 0 && isFacing(enemyDir)) {
            safeFire(enemyDir);
        }
        // REMOVED JITTER: Bot now stays focused on aim during cooldown
    }

    private void handlePatrol() {
        // Random movement when no message received
        patrolTimer--;
        
        if (patrolTimer <= 0) {
            patrolTimer = 50; // Pick new direction every 50 ticks
            
            // Random Angle:
            // Bias towards East (-PI/2 to PI/2) so they generally advance
            double randomOffset = (Math.random() * Math.PI) - (Math.PI / 2);
            patrolAngle = randomOffset;
            
            // Or fully random: patrolAngle = (Math.random() * 2 * Math.PI) - Math.PI;
        }
        
        turnTo(patrolAngle);
        if (isFacing(patrolAngle) && !isBlocked()) {
            move();
        } else if (isBlocked()) {
            patrolTimer = 0; // Force new direction immediately if blocked
        }
    }

    private void handleHunting() {
        huntTimer--;
        if (huntTimer <= 0) {
            state = STATE_PATROL; // Target expired, go back to random patrol
            return;
        }

        double angleToTarget = Math.atan2(targetY - myEstimatedY, targetX - myEstimatedX);
        turnTo(angleToTarget);
        
        if (isFacing(angleToTarget) && !isBlocked()) {
            move();
        }
    }

    private void handleDeployment() {
        double targetAngle = 0;
        if (lane == LANE_TOP) targetAngle = -ANGLE_60;
        else if (lane == LANE_BOT) targetAngle = ANGLE_60;
        else targetAngle = Parameters.EAST;
        
        turnTo(targetAngle);
        if (isFacing(targetAngle) && !isBlocked()) move();
    }
    
    private void handleUnstuck() {
        unstuckActionTimer--;
        if (unstuckActionTimer <= 0) {
            state = previousState;
            return;
        }
        if (unstuckActionTimer > UNSTUCK_DURATION / 2) moveBack();
        else {
            if (unstuckTurnRight) stepTurn(Parameters.Direction.RIGHT);
            else stepTurn(Parameters.Direction.LEFT);
        }
    }

    // --- UTILITIES ---

    private void broadcastTargetInfo(IRadarResult enemy) {
        double dist = enemy.getObjectDistance();
        double dir = enemy.getObjectDirection();
        double ex = myEstimatedX + dist * Math.cos(dir);
        double ey = myEstimatedY + dist * Math.sin(dir);
        broadcast("TARGET:" + (int)ex + ":" + (int)ey);
    }

    private void processMessages() {
        ArrayList<String> messages = fetchAllMessages();
        for (String msg : messages) {
            if (msg.startsWith("TARGET:")) {
                try {
                    String[] parts = msg.split(":");
                    double tx = Double.parseDouble(parts[1]);
                    double ty = Double.parseDouble(parts[2]);
                    
                    this.targetX = tx;
                    this.targetY = ty;
                    this.huntTimer = 200; 
                    
                    if (state != STATE_COMBAT && state != STATE_UNSTUCK) {
                        state = STATE_HUNTING;
                    }
                } catch (Exception e) { }
            }
        }
    }

    private void safeFire(double dir) {
        IFrontSensorResult front = detectFront();
        if (front.getObjectType() == IFrontSensorResult.Types.TeamMainBot || 
            front.getObjectType() == IFrontSensorResult.Types.TeamSecondaryBot) {
            return; 
        }
        fire(dir);
        fireCooldown = FIRE_LATENCY;
    }

    private void determineLane(ArrayList<IRadarResult> radar) {
        int friends = 0;
        double friendDir = 0;
        for (IRadarResult r : radar) {
            if (r.getObjectType() == IRadarResult.Types.TeamMainBot) {
                friends++;
                friendDir = r.getObjectDirection();
            }
        }
        if (friends >= 2) {
            lane = LANE_MID; myEstimatedY = 1000;
        } else if (friends == 1) {
            if (friendDir > 0) { lane = LANE_TOP; myEstimatedY = 800; }
            else { lane = LANE_BOT; myEstimatedY = 1200; }
        } else {
            lane = LANE_MID; 
        }
    }

    private void turnTo(double targetDir) {
        double current = getHeading();
        double delta = targetDir - current;
        while (delta > Math.PI) delta -= 2 * Math.PI;
        while (delta < -Math.PI) delta += 2 * Math.PI;
        if (Math.abs(delta) < 0.05) return;
        if (delta > 0) stepTurn(Parameters.Direction.RIGHT);
        else stepTurn(Parameters.Direction.LEFT);
    }
    
    private boolean isFacing(double dir) {
        double delta = Math.abs(getHeading() - dir);
        while (delta > Math.PI) delta = Math.abs(delta - 2 * Math.PI);
        return delta < 0.25; 
    }

    private boolean isBlocked() {
        IFrontSensorResult f = detectFront();
        return f.getObjectType() == IFrontSensorResult.Types.WALL ||
               f.getObjectType() == IFrontSensorResult.Types.Wreck;
    }

    private IRadarResult getBestTarget(ArrayList<IRadarResult> results) {
        IRadarResult best = null;
        double minDist = Double.MAX_VALUE;
        for (IRadarResult r : results) {
            if (isEnemy(r.getObjectType())) {
                if (r.getObjectDistance() < minDist) {
                    minDist = r.getObjectDistance();
                    best = r;
                }
            }
        }
        return best;
    }
    
    private boolean isEnemy(IRadarResult.Types type) {
        return type == IRadarResult.Types.OpponentMainBot || 
               type == IRadarResult.Types.OpponentSecondaryBot;
    }
}
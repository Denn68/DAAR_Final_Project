package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;
import java.util.ArrayList;

public class TeamBMainBotMarssoMougamadoubougary extends Brain {
    private static final int ADVANCING = 0;
    private static final int HOLDING = 1;
    private static final int DODGING = 2;

    private int currentState;
    private int previousState;
    private int stepCounter;
    private int dodgeCounter;
    private int dodgeDirection;
    private boolean dodgingPhase2;

    private double targetDirection;
    private double advanceDir;

    private int fireCounter;

    private int dodgeAttempts;

    private static final double HEADINGPRECISION = 0.01;
    private static final int ADVANCING_STEPS = 100;
    private static final int DODGE_STEPS = 50;
    private static final int FIRE_LATENCY = 1;
    private static final int MAX_DODGE_ATTEMPTS = 2;

    public TeamBMainBotMarssoMougamadoubougary() {
        super();
    }

    public void activate() {
        currentState = ADVANCING;
        previousState = ADVANCING;
        stepCounter = ADVANCING_STEPS;
        dodgeCounter = 0;
        dodgingPhase2 = false;
        fireCounter = 0;
        dodgeAttempts = 0;
        advanceDir = Parameters.WEST;
        sendLogMessage("Défenseur B activé - Mode ADVANCING");
    }

    public void step() {
        if (fireCounter > 0) fireCounter--;

        scanAndShoot();

        switch (currentState) {
            case ADVANCING:
                stepAdvancing();
                break;
            case HOLDING:
                stepHolding();
                break;
            case DODGING:
                stepDodging();
                break;
        }
    }

    private void scanAndShoot() {
        ArrayList<IRadarResult> radarResults = detectRadar();
        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) {
                targetDirection = r.getObjectDirection();
                if (fireCounter == 0) {
                    fire(targetDirection);
                    fireCounter = FIRE_LATENCY;
                }
                if (currentState == ADVANCING) {
                    currentState = HOLDING;
                    sendLogMessage("Ennemi détecté - HOLDING");
                }
                return;
            }
        }
    }

    private void stepAdvancing() {
        if (stepCounter <= 0) {
            advanceDir = (advanceDir == Parameters.WEST) ? Parameters.EAST : Parameters.WEST;
            stepCounter = ADVANCING_STEPS;
        }

        IFrontSensorResult front = detectFront();
        if (front.getObjectType() != IFrontSensorResult.Types.NOTHING &&
            front.getObjectType() != IFrontSensorResult.Types.TeamMainBot &&
            front.getObjectType() != IFrontSensorResult.Types.TeamSecondaryBot) {

            dodgeAttempts++;
            if (dodgeAttempts > MAX_DODGE_ATTEMPTS) {
                advanceDir = (advanceDir == Parameters.WEST) ? Parameters.EAST : Parameters.WEST;
                stepCounter = ADVANCING_STEPS;
                dodgeAttempts = 0;
                sendLogMessage("Obstacle - 2 esquives échouées -> Demi-tour");
                return;
            }

            previousState = ADVANCING;
            startDodging();
            return;
        }

        dodgeAttempts = 0;

        if (!isHeading(advanceDir)) {
            turnToward(advanceDir);
        } else {
            stepCounter--;
            move();
        }
    }

    private void stepHolding() {
        ArrayList<IRadarResult> radarResults = detectRadar();
        IRadarResult closestEnemy = null;
        double closestDistance = Double.MAX_VALUE;

        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) {
                if (r.getObjectDistance() < closestDistance) {
                    closestEnemy = r;
                    closestDistance = r.getObjectDistance();
                }
            }
        }

        if (closestEnemy == null) {
            sendLogMessage("Cible éliminée - Retour en ADVANCING");
            stepCounter = ADVANCING_STEPS;
            currentState = ADVANCING;
            return;
        }

        targetDirection = closestEnemy.getObjectDirection();

        if (!isHeading(targetDirection)) {
            turnToward(targetDirection);
        }

        if (fireCounter == 0) {
            fire(targetDirection);
            fireCounter = FIRE_LATENCY;
        }
    }

    private void stepDodging() {
        if (!dodgingPhase2) {
            if (dodgeCounter > 0) {
                dodgeCounter--;
                double dodgeDir = (dodgeDirection == 0) ? Parameters.NORTH : Parameters.SOUTH;
                if (!isHeading(dodgeDir)) {
                    turnToward(dodgeDir);
                } else {
                    move();
                }
            } else {
                dodgingPhase2 = true;
                dodgeCounter = DODGE_STEPS;
            }
        } else {
            if (!isHeading(advanceDir)) {
                turnToward(advanceDir);
            } else {
                sendLogMessage("Fin esquive - Retour");
                currentState = previousState;
                dodgingPhase2 = false;
            }
        }
    }

    private void startDodging() {
        currentState = DODGING;
        dodgeCounter = DODGE_STEPS;
        dodgingPhase2 = false;
        dodgeDirection = (Math.random() > 0.5) ? 0 : 1;
        sendLogMessage("Obstacle - Esquive vers " + (dodgeDirection == 0 ? "NORTH" : "SOUTH"));
    }

    private boolean isEnemy(IRadarResult.Types type) {
        return type == IRadarResult.Types.OpponentMainBot ||
               type == IRadarResult.Types.OpponentSecondaryBot;
    }

    private boolean isHeading(double dir) {
        return Math.abs(Math.sin(getHeading() - dir)) < HEADINGPRECISION;
    }

    private void turnToward(double targetDir) {
        double diff = targetDir - getHeading();
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;

        if (diff > 0) {
            stepTurn(Parameters.Direction.RIGHT);
        } else {
            stepTurn(Parameters.Direction.LEFT);
        }
    }
}

/* ******************************************************
 * Team A Secondary Bot - Scout Éclaireur
 * Stratégie: Avance, localise ennemis, signale au Main, puis esquive aléatoirement
 * ******************************************************/
package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;
import java.util.ArrayList;

public class TeamASecondaryBotMarssoMougamadoubougary extends Brain {
    // États
    private static final int ADVANCING = 0;
    private static final int EVASIVE_TURN = 1;
    private static final int EVASIVE_MOVE = 2;

    // Directions possibles
    private static final double[] DIRECTIONS = {
        Parameters.NORTH,
        Parameters.SOUTH,
        Parameters.EAST,
        Parameters.WEST
    };

    // Variables d'état
    private int currentState;
    private int moveCounter;
    private int turnCounter;
    private double currentDirection;
    private boolean enemyFound;
    private int broadcastCooldown;
    private double targetDirection;

    // Constantes
    private static final int MOVE_STEPS = 70;          // Steps pour avancer
    private static final int TURN_STEPS = 40;          // Steps pour tourner
    private static final int FIRE_LATENCY = 20;
    private static final int BROADCAST_COOLDOWN = 100;

    private int fireCounter;

    public TeamASecondaryBotMarssoMougamadoubougary() {
        super();
    }

    public void activate() {
        currentState = ADVANCING;
        moveCounter = 0;
        turnCounter = 0;
        fireCounter = 0;
        enemyFound = false;
        broadcastCooldown = 0;
        currentDirection = Parameters.EAST;
        sendLogMessage("Scout A activé - Mode ADVANCING");
    }

    public void step() {
        if (fireCounter > 0) fireCounter--;
        if (broadcastCooldown > 0) broadcastCooldown--;

        // Scanner et tirer si ennemi visible
        scanAndFire();

        switch (currentState) {
            case ADVANCING:
                stepAdvancing();
                break;
            case EVASIVE_TURN:
                stepEvasiveTurn();
                break;
            case EVASIVE_MOVE:
                stepEvasiveMove();
                break;
        }
    }

    private void scanAndFire() {
        ArrayList<IRadarResult> radarResults = detectRadar();
        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) {
                targetDirection = r.getObjectDirection();

                if (!enemyFound) {
                    enemyFound = true;
                    broadcast("ENEMY_FOUND");
                    sendLogMessage("ENNEMI TROUVÉ! Signal envoyé");
                }

                if (broadcastCooldown == 0) {
                    broadcast("ENEMY_FOUND");
                    broadcastCooldown = BROADCAST_COOLDOWN;
                }

                if (fireCounter == 0) {
                    fire(targetDirection);
                    fireCounter = FIRE_LATENCY;
                }
                return;
            }
        }
    }

    private void stepAdvancing() {
        // Chercher les ennemis
        ArrayList<IRadarResult> radarResults = detectRadar();
        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) {
                enemyFound = true;
                broadcast("ENEMY_FOUND");
                sendLogMessage("Ennemi détecté! Mode EVASIVE");
                startEvasive();
                return;
            }
        }

        // Vérifier si bloqué
        if (isBlocked()) {
            startEvasive();
            return;
        }

        // Avancer vers EAST
        if (!isHeadingTo(Parameters.EAST)) {
            turnTo(Parameters.EAST);
        } else {
            move();
        }
    }

    private void stepEvasiveTurn() {
        // Tourner vers la direction choisie
        turnCounter--;

        if (isHeadingTo(currentDirection) || turnCounter <= 0) {
            // Fini de tourner -> passer en MOVE
            currentState = EVASIVE_MOVE;
            moveCounter = MOVE_STEPS;
            sendLogMessage("Tourné -> avancer " + dirName(currentDirection));
        } else {
            turnTo(currentDirection);
        }
    }

    private void stepEvasiveMove() {
        // Avancer dans la direction
        moveCounter--;

        if (isBlocked() || moveCounter <= 0) {
            // Bloqué ou fini -> nouvelle direction aléatoire
            startEvasive();
        } else {
            move();
        }
    }

    private void startEvasive() {
        currentState = EVASIVE_TURN;
        turnCounter = TURN_STEPS;
        // Choisir une direction aléatoire
        currentDirection = DIRECTIONS[(int)(Math.random() * 4)];
        sendLogMessage("EVASIVE: tourner vers " + dirName(currentDirection));
    }

    private String dirName(double dir) {
        if (dir == Parameters.NORTH) return "NORTH";
        if (dir == Parameters.SOUTH) return "SOUTH";
        if (dir == Parameters.EAST) return "EAST";
        if (dir == Parameters.WEST) return "WEST";
        return "?";
    }

    private boolean isBlocked() {
        IFrontSensorResult front = detectFront();
        return front.getObjectType() != IFrontSensorResult.Types.NOTHING &&
               front.getObjectType() != IFrontSensorResult.Types.TeamMainBot &&
               front.getObjectType() != IFrontSensorResult.Types.TeamSecondaryBot;
    }

    private boolean isHeadingTo(double dir) {
        double diff = Math.abs(getHeading() - dir);
        while (diff > Math.PI) diff = Math.abs(diff - 2 * Math.PI);
        return diff < 0.1;
    }

    private void turnTo(double dir) {
        double diff = dir - getHeading();
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;

        if (diff > 0) stepTurn(Parameters.Direction.RIGHT);
        else stepTurn(Parameters.Direction.LEFT);
    }

    private boolean isEnemy(IRadarResult.Types type) {
        return type == IRadarResult.Types.OpponentMainBot ||
               type == IRadarResult.Types.OpponentSecondaryBot;
    }
}

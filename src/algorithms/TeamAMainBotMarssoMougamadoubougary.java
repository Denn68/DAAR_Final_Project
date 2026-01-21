/* ******************************************************
 * Team A Main Bot - Tank Chasseur
 * Stratégie: Patrouille, détecte et tire sur ennemis, esquive les obstacles
 * ******************************************************/
package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;
import java.util.ArrayList;

public class TeamAMainBotMarssoMougamadoubougary extends Brain {
    // États principaux
    private static final int PATROL = 0;
    private static final int RUSHING = 1;
    private static final int ENGAGING = 2;
    private static final int DODGE_TURN1 = 3;
    private static final int DODGE_MOVE1 = 4;
    private static final int DODGE_TURN2 = 5;
    private static final int DODGE_MOVE2 = 6;
    private static final int DODGE_TURN3 = 7;
    private static final int DODGE_MOVE3 = 8;

    // Variables d'état
    private int currentState;
    private int previousState;
    private int moveCounter;
    private int turnCounter;
    private double dodgeDirection;
    private boolean signalReceived;

    // Constantes
    private static final int TURN_STEPS = 30;
    private static final int DODGE_VERTICAL_STEPS = 60;
    private static final int DODGE_FORWARD_STEPS = 50;
    private static final int PATROL_SLOW_FACTOR = 3;
    private static final int FIRE_LATENCY = 20;

    private int fireCounter;
    private int stepCounter;

    public TeamAMainBotMarssoMougamadoubougary() {
        super();
    }

    public void activate() {
        currentState = PATROL;
        previousState = PATROL;
        stepCounter = 0;
        moveCounter = 0;
        turnCounter = 0;
        fireCounter = 0;
        signalReceived = false;
        dodgeDirection = Parameters.SOUTH;
        sendLogMessage("Tank A activé - Mode PATROL");
    }

    public void step() {
        if (fireCounter > 0) fireCounter--;

        checkMessages();

        // TOUJOURS scanner et tirer si ennemi visible (dans tous les états sauf esquive)
        if (currentState != DODGE_TURN1 && currentState != DODGE_MOVE1 &&
            currentState != DODGE_TURN2 && currentState != DODGE_MOVE2 &&
            currentState != DODGE_TURN3 && currentState != DODGE_MOVE3) {
            if (scanAndShoot()) {
                // Si on a trouvé un ennemi et qu'on n'est pas en ENGAGING, y aller
                if (currentState != ENGAGING) {
                    currentState = ENGAGING;
                    sendLogMessage("Ennemi détecté! Engagement");
                }
            }
        }

        switch (currentState) {
            case PATROL:
                stepPatrol();
                break;
            case RUSHING:
                stepRushing();
                break;
            case ENGAGING:
                stepEngaging();
                break;
            case DODGE_TURN1:
                stepDodgeTurn1();
                break;
            case DODGE_MOVE1:
                stepDodgeMove1();
                break;
            case DODGE_TURN2:
                stepDodgeTurn2();
                break;
            case DODGE_MOVE2:
                stepDodgeMove2();
                break;
            case DODGE_TURN3:
                stepDodgeTurn3();
                break;
            case DODGE_MOVE3:
                stepDodgeMove3();
                break;
        }
    }

    // Scan et tire immédiatement si ennemi trouvé. Retourne true si ennemi détecté.
    private boolean scanAndShoot() {
        ArrayList<IRadarResult> radarResults = detectRadar();
        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) {
                // Tirer immédiatement
                if (fireCounter == 0) {
                    fire(r.getObjectDirection());
                    fireCounter = FIRE_LATENCY;
                }
                return true;
            }
        }
        return false;
    }

    private void checkMessages() {
        ArrayList<String> messages = fetchAllMessages();
        for (String msg : messages) {
            if (msg.equals("ENEMY_FOUND") && !signalReceived) {
                signalReceived = true;
                if (currentState == PATROL) {
                    currentState = RUSHING;
                    sendLogMessage("Signal reçu! Rush vers scout");
                }
            }
        }
    }

    private void stepPatrol() {
        if (isBlocked()) {
            startDodge();
            return;
        }

        stepCounter++;
        if (!isHeadingTo(Parameters.EAST)) {
            turnTo(Parameters.EAST);
        } else if (stepCounter >= PATROL_SLOW_FACTOR) {
            stepCounter = 0;
            move();
        }
    }

    private void stepRushing() {
        // Chercher le scout
        ArrayList<IRadarResult> radarResults = detectRadar();
        double scoutDirection = -1;
        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.TeamSecondaryBot) {
                scoutDirection = r.getObjectDirection();
                break;
            }
        }

        if (isBlocked()) {
            startDodge();
            return;
        }

        double targetDir = (scoutDirection >= 0) ? scoutDirection : Parameters.EAST;
        if (!isHeadingTo(targetDir)) {
            turnTo(targetDir);
        } else {
            move();
        }
    }

    private void stepEngaging() {
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
            if (signalReceived) {
                currentState = RUSHING;
                sendLogMessage("Cible perdue - Retour vers scout");
            } else {
                currentState = PATROL;
                sendLogMessage("Cible perdue - Retour PATROL");
            }
            return;
        }

        double targetDirection = closestEnemy.getObjectDirection();
        double targetDistance = closestEnemy.getObjectDistance();

        // Tirer
        if (fireCounter == 0) {
            fire(targetDirection);
            fireCounter = FIRE_LATENCY;
        }

        // Se rapprocher si trop loin
        if (targetDistance > 200) {
            if (isBlocked()) {
                startDodge();
                return;
            }
            if (!isHeadingTo(targetDirection)) {
                turnTo(targetDirection);
            } else {
                move();
            }
        }
    }

    // ====== ESQUIVE EN U - Plus propre avec états séparés ======

    private void startDodge() {
        previousState = currentState;
        currentState = DODGE_TURN1;
        turnCounter = TURN_STEPS;
        dodgeDirection = (Math.random() > 0.5) ? Parameters.NORTH : Parameters.SOUTH;
        sendLogMessage("Esquive vers " + (dodgeDirection == Parameters.NORTH ? "NORTH" : "SOUTH"));
    }

    private void stepDodgeTurn1() {
        turnCounter--;
        if (isHeadingTo(dodgeDirection) || turnCounter <= 0) {
            currentState = DODGE_MOVE1;
            moveCounter = DODGE_VERTICAL_STEPS;
        } else {
            turnTo(dodgeDirection);
        }
    }

    private void stepDodgeMove1() {
        if (moveCounter <= 0) {
            currentState = DODGE_TURN2;
            turnCounter = TURN_STEPS;
            return;
        }

        if (isBlocked()) {
            // Bloqué verticalement, passer à l'avancée
            currentState = DODGE_TURN2;
            turnCounter = TURN_STEPS;
            return;
        }

        move();
        moveCounter--;
    }

    private void stepDodgeTurn2() {
        turnCounter--;
        if (isHeadingTo(Parameters.EAST) || turnCounter <= 0) {
            currentState = DODGE_MOVE2;
            moveCounter = DODGE_FORWARD_STEPS;
        } else {
            turnTo(Parameters.EAST);
        }
    }

    private void stepDodgeMove2() {
        if (moveCounter <= 0) {
            currentState = DODGE_TURN3;
            turnCounter = TURN_STEPS;
            return;
        }

        if (isBlocked()) {
            // Toujours bloqué, passer au retour
            currentState = DODGE_TURN3;
            turnCounter = TURN_STEPS;
            return;
        }

        move();
        moveCounter--;
    }

    private void stepDodgeTurn3() {
        turnCounter--;
        double inverseDir = (dodgeDirection == Parameters.NORTH) ? Parameters.SOUTH : Parameters.NORTH;
        if (isHeadingTo(inverseDir) || turnCounter <= 0) {
            currentState = DODGE_MOVE3;
            moveCounter = DODGE_VERTICAL_STEPS;
        } else {
            turnTo(inverseDir);
        }
    }

    private void stepDodgeMove3() {
        if (moveCounter <= 0) {
            endDodge();
            return;
        }

        if (isBlocked()) {
            endDodge();
            return;
        }

        move();
        moveCounter--;
    }

    private void endDodge() {
        currentState = previousState;
        sendLogMessage("Fin esquive");
    }

    // ====== UTILITAIRES ======

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

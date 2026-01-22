/* ******************************************************
 * Team B Main Bot - Défenseur Groupé
 * Stratégie: Avance groupé, si un est bloqué tous reculent ensemble
 * ******************************************************/
package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;
import java.util.ArrayList;

public class TeamBMainBotMarssoMougamadoubougary extends Brain {
    // États
    private static final int ADVANCING = 0;
    private static final int HOLDING = 1;
    private static final int RETREAT = 2;  // Reculer en groupe

    // Variables d'état
    private int currentState;
    private int stepCounter;
    private int retreatCounter;
    private double targetDirection;

    // Constantes
    private static final double HEADINGPRECISION = 0.01;
    private static final int ADVANCING_STEPS = 100;
    private static final int RETREAT_STEPS = 80;  // Reculer pendant 80 steps
    private static final int FIRE_LATENCY = 20;

    private int fireCounter;

    public TeamBMainBotMarssoMougamadoubougary() {
        super();
    }

    public void activate() {
        currentState = ADVANCING;
        stepCounter = ADVANCING_STEPS;
        retreatCounter = 0;
        fireCounter = 0;
        sendLogMessage("Défenseur B activé - Mode ADVANCING");
    }

    public void step() {
        if (fireCounter > 0) fireCounter--;

        // Écouter les messages des coéquipiers
        checkMessages();

        // TOUJOURS scanner et tirer si ennemi visible
        scanAndShoot();

        switch (currentState) {
            case ADVANCING:
                stepAdvancing();
                break;
            case HOLDING:
                stepHolding();
                break;
            case RETREAT:
                stepRetreat();
                break;
        }
    }

    private void checkMessages() {
        ArrayList<String> messages = fetchAllMessages();
        for (String msg : messages) {
            if (msg.equals("BLOCKED") && currentState != RETREAT) {
                // Un coéquipier est bloqué - tout le monde recule
                currentState = RETREAT;
                retreatCounter = RETREAT_STEPS;
                sendLogMessage("Coéquipier bloqué - RETREAT");
            }
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
                // Passer en HOLDING si on avance
                if (currentState == ADVANCING) {
                    currentState = HOLDING;
                    sendLogMessage("Ennemi détecté - HOLDING");
                }
                return;
            }
        }
    }

    private void stepAdvancing() {
        // Vérifier si bloqué
        if (isBlocked()) {
            // Signaler aux coéquipiers
            broadcast("BLOCKED");
            currentState = RETREAT;
            retreatCounter = RETREAT_STEPS;
            sendLogMessage("Bloqué - RETREAT + signal");
            return;
        }

        if (stepCounter > 0) {
            if (!isHeading(Parameters.WEST)) {
                turnToward(Parameters.WEST);
            } else {
                stepCounter--;
                move();
            }
        } else {
            stepCounter = ADVANCING_STEPS;
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
            sendLogMessage("Cible éliminée - ADVANCING");
            stepCounter = ADVANCING_STEPS;
            currentState = ADVANCING;
            return;
        }

        targetDirection = closestEnemy.getObjectDirection();

        // Tourner vers l'ennemi
        if (!isHeading(targetDirection)) {
            turnToward(targetDirection);
        }

        // Tirer
        if (fireCounter == 0) {
            fire(targetDirection);
            fireCounter = FIRE_LATENCY;
        }
    }

    private void stepRetreat() {
        retreatCounter--;

        if (retreatCounter <= 0) {
            // Fin du recul - reprendre l'avancée
            currentState = ADVANCING;
            stepCounter = ADVANCING_STEPS;
            sendLogMessage("Fin RETREAT - ADVANCING");
            return;
        }

        // Reculer vers EAST (direction opposée à WEST)
        if (!isHeading(Parameters.EAST)) {
            turnToward(Parameters.EAST);
        } else {
            // Vérifier si on peut reculer
            IFrontSensorResult front = detectFront();
            if (front.getObjectType() == IFrontSensorResult.Types.NOTHING ||
                front.getObjectType() == IFrontSensorResult.Types.TeamMainBot ||
                front.getObjectType() == IFrontSensorResult.Types.TeamSecondaryBot) {
                move();
            }
            // Si bloqué derrière, on attend juste
        }
    }

    private boolean isBlocked() {
        IFrontSensorResult front = detectFront();
        return front.getObjectType() != IFrontSensorResult.Types.NOTHING &&
               front.getObjectType() != IFrontSensorResult.Types.TeamMainBot &&
               front.getObjectType() != IFrontSensorResult.Types.TeamSecondaryBot;
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

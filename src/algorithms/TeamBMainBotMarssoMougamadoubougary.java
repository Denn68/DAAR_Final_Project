/* ******************************************************
 * Team B Main Bot - Défenseur
 * Stratégie: Positionne, avance, scanne 360°, tient et tire
 * ******************************************************/
package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;
import java.util.ArrayList;

public class TeamBMainBotMarssoMougamadoubougary extends Brain {
    // États
    private static final int POSITIONING = 0;
    private static final int ADVANCING = 1;
    private static final int SCANNING = 2;
    private static final int HOLDING = 3;
    private static final int DODGING = 4;

    // Variables d'état
    private int currentState;
    private int previousState; // Pour revenir après DODGING
    private int stepCounter;
    private int dodgeCounter;
    private int dodgeDirection; // 0 = NORTH, 1 = SOUTH
    private boolean dodgingPhase2;

    // Scan 360°
    private double scanStartHeading;
    private double totalScanned;

    // Cible en cours
    private double targetDirection;
    private double targetDistance;

    // Constantes
    private static final double HEADINGPRECISION = 0.01;
    private static final int POSITIONING_STEPS = 100;
    private static final int ADVANCING_STEPS = 80;
    private static final int DODGE_STEPS = 50;
    private static final int FIRE_LATENCY = 20;

    private int fireCounter;

    public TeamBMainBotMarssoMougamadoubougary() {
        super();
    }

    public void activate() {
        currentState = POSITIONING;
        previousState = ADVANCING;
        stepCounter = POSITIONING_STEPS;
        dodgeCounter = 0;
        dodgingPhase2 = false;
        fireCounter = 0;
        totalScanned = 0;
        sendLogMessage("Défenseur B activé - Mode POSITIONING");
    }

    public void step() {
        // Décrémenter compteur de tir
        if (fireCounter > 0) fireCounter--;

        switch (currentState) {
            case POSITIONING:
                stepPositioning();
                break;
            case ADVANCING:
                stepAdvancing();
                break;
            case SCANNING:
                stepScanning();
                break;
            case HOLDING:
                stepHolding();
                break;
            case DODGING:
                stepDodging();
                break;
        }
    }

    private void stepPositioning() {
        // Descendre vers SOUTH au début
        if (stepCounter > 0) {
            // Vérifier si ennemi en vue pendant le positionnement
            ArrayList<IRadarResult> radarResults = detectRadar();
            for (IRadarResult r : radarResults) {
                if (isEnemy(r.getObjectType())) {
                    targetDirection = r.getObjectDirection();
                    targetDistance = r.getObjectDistance();
                    currentState = HOLDING;
                    sendLogMessage("Ennemi pendant positionnement - HOLDING");
                    return;
                }
            }

            if (!isHeading(Parameters.SOUTH)) {
                turnToward(Parameters.SOUTH);
            } else {
                IFrontSensorResult front = detectFront();
                if (front.getObjectType() == IFrontSensorResult.Types.NOTHING) {
                    stepCounter--;
                    move();
                } else {
                    // Bloqué - passer directement en ADVANCING
                    stepCounter = ADVANCING_STEPS;
                    currentState = ADVANCING;
                    sendLogMessage("Fin positionnement (bloqué) - ADVANCING");
                }
            }
        } else {
            // Fin du positionnement - passer en ADVANCING
            stepCounter = ADVANCING_STEPS;
            currentState = ADVANCING;
            sendLogMessage("Fin positionnement - ADVANCING");
        }
    }

    private void stepAdvancing() {
        // Vérifier si ennemi en vue
        ArrayList<IRadarResult> radarResults = detectRadar();
        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) {
                targetDirection = r.getObjectDirection();
                targetDistance = r.getObjectDistance();
                currentState = HOLDING;
                sendLogMessage("Ennemi détecté - HOLDING");
                return;
            }
        }

        // Avancer pendant ~80 steps
        if (stepCounter > 0) {
            // Vérifier si bloqué
            IFrontSensorResult front = detectFront();
            if (front.getObjectType() != IFrontSensorResult.Types.NOTHING &&
                front.getObjectType() != IFrontSensorResult.Types.TeamMainBot &&
                front.getObjectType() != IFrontSensorResult.Types.TeamSecondaryBot) {
                previousState = ADVANCING;
                startDodging();
                return;
            }

            if (!isHeading(Parameters.WEST)) {
                turnToward(Parameters.WEST);
            } else {
                stepCounter--;
                move();
            }
        } else {
            // Fin de l'avancée - passer en SCANNING
            currentState = SCANNING;
            scanStartHeading = getHeading();
            totalScanned = 0;
            sendLogMessage("Fin avancée - SCANNING 360°");
        }
    }

    private void stepScanning() {
        // Vérifier si ennemi trouvé pendant le scan
        ArrayList<IRadarResult> radarResults = detectRadar();
        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) {
                targetDirection = r.getObjectDirection();
                targetDistance = r.getObjectDistance();
                currentState = HOLDING;
                sendLogMessage("Ennemi trouvé pendant scan - HOLDING");
                return;
            }
        }

        // Rotation 360°
        stepTurn(Parameters.Direction.RIGHT);
        totalScanned += Parameters.teamBMainBotStepTurnAngle;

        // Vérifier si on a fait un tour complet
        if (totalScanned >= 2 * Math.PI) {
            // Scan complet sans ennemi - retour en ADVANCING
            stepCounter = ADVANCING_STEPS;
            currentState = ADVANCING;
            sendLogMessage("Scan 360° complet - Retour en ADVANCING");
        }
    }

    private void stepHolding() {
        // S'ARRÊTER et rester en place
        // Scanner pour retrouver l'ennemi
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
            // Ennemi mort - retour en ADVANCING
            sendLogMessage("Cible éliminée - Retour en ADVANCING");
            stepCounter = ADVANCING_STEPS;
            currentState = ADVANCING;
            return;
        }

        // Mettre à jour la direction vers l'ennemi
        targetDirection = closestEnemy.getObjectDirection();
        targetDistance = closestEnemy.getObjectDistance();

        // Tourner vers l'ennemi
        if (!isHeading(targetDirection)) {
            turnToward(targetDirection);
        }

        // TIRER sur l'ennemi
        if (fireCounter == 0) {
            fire(targetDirection);
            fireCounter = FIRE_LATENCY;
        }

        // Le défenseur reste en place et tire - pas de mouvement en HOLDING
    }

    private void stepDodging() {
        if (!dodgingPhase2) {
            // Phase 1: Monter ou descendre
            if (dodgeCounter > 0) {
                dodgeCounter--;
                double dodgeDir = (dodgeDirection == 0) ? Parameters.NORTH : Parameters.SOUTH;
                if (!isHeading(dodgeDir)) {
                    turnToward(dodgeDir);
                } else {
                    move();
                }
            } else {
                // Phase 2: Revenir vers WEST
                dodgingPhase2 = true;
                dodgeCounter = DODGE_STEPS;
            }
        } else {
            // Phase 2: Retourner vers la direction principale
            if (!isHeading(Parameters.WEST)) {
                turnToward(Parameters.WEST);
            } else {
                // Retour à l'état précédent
                sendLogMessage("Fin esquive - Retour en " + previousState);
                currentState = previousState;
                stepCounter = ADVANCING_STEPS; // Reset du compteur
                dodgingPhase2 = false;
            }
        }
    }

    private void startDodging() {
        currentState = DODGING;
        dodgeCounter = DODGE_STEPS;
        dodgingPhase2 = false;
        // Choisir direction basée sur position (alternance simple)
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
        // Normaliser entre -PI et PI
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;

        if (diff > 0) {
            stepTurn(Parameters.Direction.RIGHT);
        } else {
            stepTurn(Parameters.Direction.LEFT);
        }
    }
}

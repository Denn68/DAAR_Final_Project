/* ******************************************************
 * Team A Secondary Bot - Scout Chasseur
 * Stratégie: Avance, cherche ennemis, signale et tire
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
    private static final int HUNTING = 1;
    private static final int DODGING = 2;

    // Variables d'état
    private int currentState;
    private int dodgeCounter;
    private int dodgeDirection; // 0 = NORTH, 1 = SOUTH
    private boolean dodgingPhase2; // true = retour vers EAST

    // Cible en cours de chasse
    private double targetDirection;
    private double targetDistance;

    // Constantes
    private static final double HEADINGPRECISION = 0.01;
    private static final int DODGE_STEPS = 50;
    private static final double HUNTING_DISTANCE = 300;
    private static final int FIRE_LATENCY = 20;

    private int fireCounter;

    public TeamASecondaryBotMarssoMougamadoubougary() {
        super();
    }

    public void activate() {
        currentState = ADVANCING;
        dodgeCounter = 0;
        dodgingPhase2 = false;
        fireCounter = 0;
        sendLogMessage("Scout A activé - Mode ADVANCING");
    }

    public void step() {
        // Décrémenter compteur de tir
        if (fireCounter > 0) fireCounter--;

        switch (currentState) {
            case ADVANCING:
                stepAdvancing();
                break;
            case HUNTING:
                stepHunting();
                break;
            case DODGING:
                stepDodging();
                break;
        }
    }

    private void stepAdvancing() {
        // Scanner le radar pour les ennemis
        ArrayList<IRadarResult> radarResults = detectRadar();

        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) {
                // Ennemi trouvé - signaler et passer en HUNTING
                targetDirection = r.getObjectDirection();
                targetDistance = r.getObjectDistance();

                // Broadcast position aux coéquipiers
                broadcast("ENEMY:" + targetDirection + ":" + targetDistance);
                sendLogMessage("Ennemi détecté! Direction: " + targetDirection);

                currentState = HUNTING;
                return;
            }
        }

        // Vérifier si bloqué
        IFrontSensorResult front = detectFront();
        if (front.getObjectType() != IFrontSensorResult.Types.NOTHING) {
            startDodging();
            return;
        }

        // Continuer à avancer vers EAST
        if (!isHeading(Parameters.EAST)) {
            turnToward(Parameters.EAST);
        } else {
            move();
        }
    }

    private void stepHunting() {
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
            // Ennemi perdu ou mort - retour en ADVANCING
            sendLogMessage("Cible perdue - Retour en ADVANCING");
            currentState = ADVANCING;
            return;
        }

        // Mettre à jour la cible
        targetDirection = closestEnemy.getObjectDirection();
        targetDistance = closestEnemy.getObjectDistance();

        // Tirer sur l'ennemi
        if (fireCounter == 0) {
            fire(targetDirection);
            fireCounter = FIRE_LATENCY;
        }

        // Se rapprocher si trop loin (garder distance ~300)
        if (targetDistance > HUNTING_DISTANCE + 50) {
            // Vérifier si bloqué
            IFrontSensorResult front = detectFront();
            if (front.getObjectType() != IFrontSensorResult.Types.NOTHING) {
                startDodging();
                return;
            }

            // Se tourner vers l'ennemi et avancer
            if (!isHeading(targetDirection)) {
                turnToward(targetDirection);
            } else {
                move();
            }
        } else if (targetDistance < HUNTING_DISTANCE - 50) {
            // Trop proche - reculer un peu
            moveBack();
        }
        // Sinon rester à distance et continuer à tirer
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
                // Phase 2: Revenir vers EAST
                dodgingPhase2 = true;
                dodgeCounter = DODGE_STEPS;
            }
        } else {
            // Phase 2: Retourner vers EAST
            if (!isHeading(Parameters.EAST)) {
                turnToward(Parameters.EAST);
            } else {
                // Retour en ADVANCING
                sendLogMessage("Fin esquive - Retour en ADVANCING");
                currentState = ADVANCING;
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
        sendLogMessage("Obstacle détecté - Esquive vers " + (dodgeDirection == 0 ? "NORTH" : "SOUTH"));
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

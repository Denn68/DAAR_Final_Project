/* ******************************************************
 * Team B Secondary Bot - Kamikaze
 * Stratégie: Cherche ennemis, fonce dessus en tirant
 * ******************************************************/
package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;
import java.util.ArrayList;

public class TeamBSecondaryBotMarssoMougamadoubougary extends Brain {
    // États
    private static final int SEARCHING = 0;
    private static final int SUICIDE = 1;
    private static final int DODGING = 2;

    // Variables d'état
    private int currentState;
    private int dodgeCounter;
    private int dodgeDirection; // 0 = NORTH, 1 = SOUTH
    private boolean dodgingPhase2;

    // Cible suicide
    private double targetDirection;

    // Constantes
    private static final double HEADINGPRECISION = 0.01;
    private static final int DODGE_STEPS = 50;
    private static final int FIRE_LATENCY = 20;

    private int fireCounter;

    public TeamBSecondaryBotMarssoMougamadoubougary() {
        super();
    }

    public void activate() {
        currentState = SEARCHING;
        dodgeCounter = 0;
        dodgingPhase2 = false;
        fireCounter = 0;
        sendLogMessage("Kamikaze B activé - Mode SEARCHING");
    }

    public void step() {
        // Décrémenter compteur de tir
        if (fireCounter > 0) fireCounter--;

        switch (currentState) {
            case SEARCHING:
                stepSearching();
                break;
            case SUICIDE:
                stepSuicide();
                break;
            case DODGING:
                stepDodging();
                break;
        }
    }

    private void stepSearching() {
        // Scanner le radar pour les ennemis
        ArrayList<IRadarResult> radarResults = detectRadar();

        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) {
                // Ennemi trouvé - passer en mode SUICIDE
                targetDirection = r.getObjectDirection();
                currentState = SUICIDE;
                sendLogMessage("ENNEMI DÉTECTÉ! MODE SUICIDE ACTIVÉ!");
                return;
            }
        }

        // Vérifier si bloqué
        IFrontSensorResult front = detectFront();
        if (front.getObjectType() != IFrontSensorResult.Types.NOTHING &&
            front.getObjectType() != IFrontSensorResult.Types.TeamMainBot &&
            front.getObjectType() != IFrontSensorResult.Types.TeamSecondaryBot) {
            startDodging();
            return;
        }

        // Continuer à avancer vers WEST
        if (!isHeading(Parameters.WEST)) {
            turnToward(Parameters.WEST);
        } else {
            move();
        }
    }

    private void stepSuicide() {
        // Scanner pour retrouver l'ennemi le plus proche
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

        if (closestEnemy != null) {
            // Mettre à jour la direction vers l'ennemi
            targetDirection = closestEnemy.getObjectDirection();
        }
        // Si ennemi perdu, continuer dans la même direction

        // TIRER CONTINUELLEMENT
        if (fireCounter == 0) {
            fire(targetDirection);
            fireCounter = FIRE_LATENCY;
        }

        // FONCER SUR L'ENNEMI - PAS DE VÉRIFICATION DE BLOCAGE
        // Le kamikaze ne s'arrête jamais!
        if (!isHeading(targetDirection)) {
            turnToward(targetDirection);
        } else {
            move();
        }

        // Ne jamais retourner en SEARCHING - suicide jusqu'à la mort
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
            // Phase 2: Retourner vers WEST
            if (!isHeading(Parameters.WEST)) {
                turnToward(Parameters.WEST);
            } else {
                // Retour en SEARCHING
                sendLogMessage("Fin esquive - Retour en SEARCHING");
                currentState = SEARCHING;
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

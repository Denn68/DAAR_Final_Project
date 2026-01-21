/* ******************************************************
 * Team A Main Bot - Tank Chasseur
 * Stratégie: Patrouille lentement, accélère sur signal, élimine
 * ******************************************************/
package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;
import java.util.ArrayList;

public class TeamAMainBotMarssoMougamadoubougary extends Brain {
    // États
    private static final int PATROL = 0;
    private static final int RUSHING = 1;
    private static final int ENGAGING = 2;
    private static final int DODGING = 3;

    // Variables d'état
    private int currentState;
    private int stepCounter; // Pour la patrouille lente
    private int dodgeCounter;
    private int dodgeDirection; // 0 = NORTH, 1 = SOUTH
    private boolean dodgingPhase2;

    // Cible en cours d'engagement
    private double targetDirection;
    private double targetDistance;

    // Constantes
    private static final double HEADINGPRECISION = 0.01;
    private static final int DODGE_STEPS = 50;
    private static final int PATROL_SLOW_FACTOR = 3; // Bouger 1 fois sur 3 steps
    private static final double ENGAGE_DISTANCE = 400;
    private static final int FIRE_LATENCY = 20;

    private int fireCounter;

    public TeamAMainBotMarssoMougamadoubougary() {
        super();
    }

    public void activate() {
        currentState = PATROL;
        stepCounter = 0;
        dodgeCounter = 0;
        dodgingPhase2 = false;
        fireCounter = 0;
        sendLogMessage("Tank A activé - Mode PATROL");
    }

    public void step() {
        // Décrémenter compteur de tir
        if (fireCounter > 0) fireCounter--;

        // Écouter les messages des scouts
        checkMessages();

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
            case DODGING:
                stepDodging();
                break;
        }
    }

    private void checkMessages() {
        ArrayList<String> messages = fetchAllMessages();
        for (String msg : messages) {
            if (msg.startsWith("ENEMY:") && currentState == PATROL) {
                // Parser le message "ENEMY:direction:distance"
                String[] parts = msg.split(":");
                if (parts.length >= 3) {
                    try {
                        targetDirection = Double.parseDouble(parts[1]);
                        targetDistance = Double.parseDouble(parts[2]);
                        currentState = RUSHING;
                        sendLogMessage("Signal reçu! Rush vers ennemi");
                    } catch (NumberFormatException e) {
                        // Ignorer message malformé
                    }
                }
            }
        }
    }

    private void stepPatrol() {
        // Scanner pour ennemis directs
        ArrayList<IRadarResult> radarResults = detectRadar();
        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) {
                targetDirection = r.getObjectDirection();
                targetDistance = r.getObjectDistance();
                currentState = ENGAGING;
                sendLogMessage("Ennemi en vue direct! Engagement");
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

        // Avancer LENTEMENT (1 move sur 3 steps)
        stepCounter++;
        if (!isHeading(Parameters.EAST)) {
            turnToward(Parameters.EAST);
        } else if (stepCounter >= PATROL_SLOW_FACTOR) {
            stepCounter = 0;
            move();
        }
    }

    private void stepRushing() {
        // Scanner pour ennemis
        ArrayList<IRadarResult> radarResults = detectRadar();
        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) {
                targetDirection = r.getObjectDirection();
                targetDistance = r.getObjectDistance();
                currentState = ENGAGING;
                sendLogMessage("Ennemi trouvé pendant rush! Engagement");
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

        // Foncer vers EAST à pleine vitesse
        if (!isHeading(Parameters.EAST)) {
            turnToward(Parameters.EAST);
        } else {
            move();
        }
    }

    private void stepEngaging() {
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
            // Ennemi mort ou perdu - retour en PATROL
            sendLogMessage("Cible éliminée/perdue - Retour en PATROL");
            currentState = PATROL;
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

        // Gérer la distance (rester à ~400)
        if (targetDistance > ENGAGE_DISTANCE + 50) {
            // Vérifier si bloqué
            IFrontSensorResult front = detectFront();
            if (front.getObjectType() != IFrontSensorResult.Types.NOTHING &&
                front.getObjectType() != IFrontSensorResult.Types.TeamMainBot &&
                front.getObjectType() != IFrontSensorResult.Types.TeamSecondaryBot) {
                startDodging();
                return;
            }

            // Se rapprocher
            if (!isHeading(targetDirection)) {
                turnToward(targetDirection);
            } else {
                move();
            }
        } else if (targetDistance < ENGAGE_DISTANCE - 50) {
            // Trop proche - reculer
            moveBack();
        }
        // Sinon rester en place et tirer
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
                // Retour en PATROL
                sendLogMessage("Fin esquive - Retour en PATROL");
                currentState = PATROL;
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

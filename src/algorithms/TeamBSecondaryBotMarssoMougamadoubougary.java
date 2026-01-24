/* ******************************************************
 * Team B Secondary Bot - Kamikaze
 * Stratégie: Cherche ennemis, fonce dessus en tirant sans jamais s'arrêter
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

    // Variables d'état
    private int currentState;
    private double targetDirection;

    // Constantes
    private static final double HEADINGPRECISION = 0.01;
    private static final int FIRE_LATENCY = 20;

    private int fireCounter;

    public TeamBSecondaryBotMarssoMougamadoubougary() {
        super();
    }

    public void activate() {
        currentState = SEARCHING;
        fireCounter = 0;
        sendLogMessage("Kamikaze B activé - Mode SEARCHING");
    }

    public void step() {
        if (fireCounter > 0) fireCounter--;

        switch (currentState) {
            case SEARCHING:
                stepSearching();
                break;
            case SUICIDE:
                stepSuicide();
                break;
        }
    }

    private void stepSearching() {
        // Scanner le radar pour les ennemis
        ArrayList<IRadarResult> radarResults = detectRadar();

        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) {
                targetDirection = r.getObjectDirection();
                currentState = SUICIDE;
                sendLogMessage("ENNEMI DÉTECTÉ! MODE SUICIDE ACTIVÉ!");
                return;
            }
        }

        // Avancer vers WEST (ignorer les obstacles - kamikaze n'a pas peur)
        if (!isHeading(Parameters.WEST)) {
            turnToward(Parameters.WEST);
        } else {
            move();
        }
    }

    private void stepSuicide() {
        // Scanner pour l'ennemi le plus proche
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
            // Ennemi mort / plus visible -> retour en SEARCHING
            currentState = SEARCHING;
            sendLogMessage("ENNEMI PERDU/ÉLIMINÉ - Retour SEARCHING");
            return;
        }

        targetDirection = closestEnemy.getObjectDirection();

        // TIRER
        if (fireCounter == 0) {
            fire(targetDirection);
            fireCounter = FIRE_LATENCY;
        }

        // FONCER (jamais d'arrêt)
        if (!isHeading(targetDirection)) {
            turnToward(targetDirection);
        } else {
            move();
        }
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

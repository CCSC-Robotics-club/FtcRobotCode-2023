package org.firstinspires.ftc.teamcode.Robot;

import com.qualcomm.robotcore.hardware.DcMotor;

import org.firstinspires.ftc.robotcore.external.Telemetry;

public class ArmControllingMethods {

    private HardwareDriver hr;

    private Telemetry telemetry;

    private boolean claw;

    private short arm;

    public ArmControllingMethods(HardwareDriver hr, Telemetry telemetry) {
        this.hr = hr;
        this.telemetry = telemetry;
        this.claw = false;
        hr.claw.setPosition(0.6);
        deactivateArm();
    }

    public void lowerArm() {
        System.out.println(arm);
        switch (arm) {
            case 3: toMidArmPosition(); break;
            case 2: toLowArmPosition(); break;
            case 1: toGroundArmPosition(); break;
            case 0: deactivateArm(); break;
        }
    }

    public void raiseArm() {
        switch (arm) {
            case -1: case 0: toLowArmPosition(); break;
            case 1: toMidArmPosition(); break;
            case 2: toHighArmPosition(); break;
        }
    }

    public void toHighArmPosition() {
        // highest position of the arm
        int highPos = 700;
        elevateArm(highPos);
        arm = 3;
        telemetry.addData("going to top_pos", highPos);
    }

    public void toMidArmPosition() {
        // midpoint position of the arm
        int midPos = 450;
        elevateArm(midPos);
        arm = 2;
        telemetry.addData("going to mid_pos", midPos);
    }

    public void toLowArmPosition() {
        // position of the arm when grabbing stuff
        int lowPos = 280;
        elevateArm(lowPos);
        arm = 1;
        telemetry.addData("going to low_pos", lowPos);
    }

    public void toGroundArmPosition() {
        // lowest position of the arm
        int gndPos = 45;
        elevateArm(gndPos);
        arm = 0;
        telemetry.addData("going to gnd_pos", gndPos);
    }

    public void open_closeClaw() {
        System.out.println("open_close");
        if(claw) {closeClaw(); return;}
        openClaw();
    }

    public void openClaw() {
        System.out.println("opening");
        claw = true;
        hr.claw.setPosition(.35); // open grabber
        // while (Math.abs(hr.claw.getPosition() - .35) > .05) Thread.yield(); // wait until the movement is finished, accept any inaccuracy below 5%
    }

    public void closeClaw() {
        System.out.println("closing");
        claw = false;
        hr.claw.setPosition(.65); // close grabber
        // while (Math.abs(hr.claw.getPosition() - .65) > .05) Thread.yield();
    }


    private void elevateArm(int position) {
        boolean isDecline = position < hr.lift_left.getCurrentPosition();

        if (isDecline) {
            double armDeclineSpeed = 0.2;
            hr.lift_left.setPower(armDeclineSpeed);
            hr.lift_right.setPower(armDeclineSpeed);
        } else {
            double armInclineSpeed = 0.4;
            hr.lift_left.setPower(armInclineSpeed);
            hr.lift_right.setPower(armInclineSpeed);
        } // set the power of the motor

        hr.lift_left.setTargetPosition(position);
        hr.lift_left.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        hr.lift_right.setTargetPosition(position);
        hr.lift_right.setMode(DcMotor.RunMode.RUN_TO_POSITION); // move the motor to position

        if (!isDecline) { // if the arm isn't declining, move to the position directly
            while (Math.abs(hr.lift_left.getCurrentPosition()-position) > 5 | Math.abs(hr.lift_right.getCurrentPosition()-position) > 5) Thread.yield(); // wait until the movement is completed
            return;
        }

        // if the arm is declining, deceleration precess is necessary
        while (Math.abs(hr.lift_left.getCurrentPosition()-position) > 20 | Math.abs(hr.lift_right.getCurrentPosition()-position) > 20) Thread.yield(); // wait until the movement almost complete
        //slow the motor down
        hr.lift_left.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        hr.lift_right.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        hr.lift_right.setVelocity(0);
        hr.lift_left.setVelocity(0);
        while (Math.abs(hr.lift_left.getVelocity()) < 10) Thread.yield(); // wait until the slow-down is completed, accept any deviation less than 3
        // set the desired position
        hr.lift_left.setTargetPosition(position);
        hr.lift_left.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        hr.lift_right.setTargetPosition(position);
        hr.lift_right.setMode(DcMotor.RunMode.RUN_TO_POSITION); // make the motor stick in the position
    }

    public void deactivateArm() {
        /*if (
                arm == -1 |
                (!claw) // if the claw is set to be closed
        ) return; // if the arm is already deactivated, or if the claw is holding stuff, abort*/
        while (arm > 0) lowerArm(); // put the arm down step by step
        openClaw();
        hr.lift_left.setPower(0);
        hr.lift_right.setPower(0);
        arm = -1;
    }

    public boolean getClaw() {
        return claw;
    }
}
/*
 * Copyright (C) 2026 Frank Augustin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package de.franka.offsetcalc;

public final class ConsoleUtil {

    private ConsoleUtil() {
    }

    public static void log(
            String message) {

        System.out.println(
                "OffsetCalc: "
                        + message
        );
    }

    public static void error(
            String message,
            Throwable throwable) {

        System.err.println(
                "OffsetCalc: "
                        + message
        );

        if (throwable != null) {
            throwable.printStackTrace();
        }
    }
}
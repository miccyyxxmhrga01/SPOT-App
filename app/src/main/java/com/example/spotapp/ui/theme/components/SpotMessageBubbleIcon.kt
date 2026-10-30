package com.example.spotapp.ui.theme.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp


@Composable
fun SpotMessageBubbleIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Black
) {

    Canvas(
        modifier = modifier
    ) {

        val width =
            size.width

        val height =
            size.height


        val bubble =
            Path().apply {

                moveTo(
                    width * 0.50f,
                    height * 0.10f
                )

                cubicTo(
                    width * 0.76f,
                    height * 0.10f,
                    width * 0.92f,
                    height * 0.26f,
                    width * 0.92f,
                    height * 0.47f
                )

                cubicTo(
                    width * 0.92f,
                    height * 0.69f,
                    width * 0.74f,
                    height * 0.83f,
                    width * 0.50f,
                    height * 0.83f
                )

                cubicTo(
                    width * 0.41f,
                    height * 0.83f,
                    width * 0.33f,
                    height * 0.81f,
                    width * 0.26f,
                    height * 0.78f
                )

                lineTo(
                    width * 0.10f,
                    height * 0.88f
                )

                lineTo(
                    width * 0.16f,
                    height * 0.68f
                )

                cubicTo(
                    width * 0.11f,
                    height * 0.61f,
                    width * 0.08f,
                    height * 0.55f,
                    width * 0.08f,
                    height * 0.47f
                )

                cubicTo(
                    width * 0.08f,
                    height * 0.26f,
                    width * 0.25f,
                    height * 0.10f,
                    width * 0.50f,
                    height * 0.10f
                )

                close()
            }


        drawPath(
            path =
                bubble,

            color =
                color,

            style =
                Stroke(
                    width =
                        1.8.dp.toPx(),

                    cap =
                        StrokeCap.Round,

                    join =
                        StrokeJoin.Round
                )
        )


        val dotRadius =
            width * 0.045f

        val dotY =
            height * 0.47f


        drawCircle(
            color =
                color,

            radius =
                dotRadius,

            center =
                Offset(
                    width * 0.38f,
                    dotY
                )
        )


        drawCircle(
            color =
                color,

            radius =
                dotRadius,

            center =
                Offset(
                    width * 0.50f,
                    dotY
                )
        )


        drawCircle(
            color =
                color,

            radius =
                dotRadius,

            center =
                Offset(
                    width * 0.62f,
                    dotY
                )
        )
    }
}
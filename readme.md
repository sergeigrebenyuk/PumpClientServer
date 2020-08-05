# Remotely controlled DIY peristaltic pump(s) and server app
DIY WiFi-equipped peristaltic pumps remotely controlled by a Java app

## Design considerations

I run several microfluidic experiments in parallel and need each flow (rate/direction patterns) to be controlled independently and remotely. On the other hand, some basic manual control of the flow (like constant flow rate and flow direction) should be also available when setting up experiment.
The settings, done manually or remotely, should be logged on server.

## System Overview
The overall idea is presented below:

![](https://github.com/sergeigrebenyuk/PumpClientServer/blob/master/NetworkDiagram.png)

### Device and server discovery
...
### Remote / local mode of operation
...

## Hardware 

The experiments are long and the pumps are supposed to work 24/7 work during 1-2 months period, so I decided to avoid the cheapest plastic pumps that one can get for 5-20 bucks on the internet. After searching around I have chosen a nicely and solidly built Boxer pump head with stepper motor. There is an option to get the pump head with stepper driver included, but it almost doubled the price, while still requiring some interfacing.

Boxer pump head equipped with stepper motor (9QX Series, 24V Stepper, 6 Rollers, Part Number: 9600.760):

![](https://www.boxerpumps.com/typo3temp/fl_realurl_image/9qx-miniature-perstaltic-pump-62.jpg)
[Manufacturer link](https://www.boxerpumps.com/en/products/peristaltic-pumps-liquid/boxer-9qx-miniature-peristaltic-pump-series/)

As I decided to get the pump without built in driver, I needed a substitute: an excellent stepper driver IC, TMC2100, framed on a developer board.

![](https://encrypted-tbn0.gstatic.com/images?q=tbn%3AANd9GcQxtcPbmj9cT7RT2vIOecWzXa7AXuDrOOoMiQ&usqp=CAU)
[Useful tips on usage](https://reprap.org/wiki/TMC2100)

The work of the pump and communication with server is implemented on Arduino UNO WiFi Rev.2.

![](https://store-cdn.arduino.cc/uni/catalog/product/cache/1/image/520x330/604a3538c15e081937dbfbd20aa60aad/a/b/abx00021_featured_2.jpg)
[Arduino Store](https://store-cdn.arduino.cc/uni/catalog/product/cache/1/image/520x330/604a3538c15e081937dbfbd20aa60aad/a/b/abx00021_featured_2.jpg)



Parts are assembled in a 3D-FDM printed case (SolidEdge 9.0 CAD files are in /CadDrawings folder)


### Wiring Diagram
![](https://github.com/sergeigrebenyuk/PumpClientServer/blob/master/PumpDiagram.png)

### Full list of parts

package main // This would be your binding package, e.g., alongside the existing amneziawg-go bindings

import "C"
import (
	"context"

	"github.com/amnezia-vpn/amneziawg-go/device"
	wireproxyawg "github.com/artem-russkikh/wireproxy-awg"
)

var (
	ctx        context.Context
	cancelFunc context.CancelFunc
	tunnel     *device.Device
)

//export awgStartWireproxy
func awgStartWireproxy(config string) error {
	logger := newAndroidLogger("Wireproxy")

	logger.Verbosef("Parsing config")
	conf, err := wireproxyawg.ParseConfigString(config)
	if err != nil {
		return err
	}

	logger.Verbosef("Starting amwg tun")
	tun, err := wireproxyawg.StartWireguard(conf.Device, logger)
	if err != nil {
		logger.Errorf("Starting tunnel failed: %v", err)
		return err
	}

	logger.Verbosef("Creating context..")
	// Create cancellable context
	ctx, cancelFunc = context.WithCancel(context.Background())

	// Spawn all routines with context
	for _, spawner := range conf.Routines {
		logger.Verbosef("Spawning routine..")
		go func(s wireproxyawg.RoutineSpawner) {
			if err := s.SpawnRoutine(ctx, tun); err != nil {
				logger.Errorf("Routine failed: %v", err)
			}
		}(spawner)
	}

	// debug/info server
	//if infoAddr != "" {
	//	go func() {
	//		err := http.ListenAndServe(infoAddr, tun)
	//		if err != nil {
	//			log.Println("Info server failed:", err)
	//		}
	//	}()
	//}

	logger.Verbosef("Waiting for done")

	<-ctx.Done() // Block until stop is called

	return nil
}

//export awgStopWireproxy
func awgStopWireproxy() error {
	// Signal routines to stop
	if cancelFunc != nil {
		cancelFunc()
	}

	// Close WireGuard device
	if tunnel != nil {
		tunnel.Close()
	}

	return nil
}

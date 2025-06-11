package awg

import (
	"errors"
	"time"

	"go.uber.org/atomic"
)

// TODO: atomic/ and better way to use this
var PacketCounter *atomic.Uint64 = atomic.NewUint64(0)

type SpecialHandshakeHandler struct {
	isFirstDone    bool
	SpecialJunk    TagJunkGeneratorHandler
	ControlledJunk TagJunkGeneratorHandler

	nextItime time.Time
	ITimeout  time.Duration // seconds

	IsSet bool
}

func (handler *SpecialHandshakeHandler) Validate() error {
	var errs []error
	if err := handler.SpecialJunk.Validate(); err != nil {
		errs = append(errs, err)
	}
	if err := handler.ControlledJunk.Validate(); err != nil {
		errs = append(errs, err)
	}
	return errors.Join(errs...)
}

func (handler *SpecialHandshakeHandler) GenerateSpecialJunk() [][]byte {
	if !handler.SpecialJunk.IsDefined() {
		return nil
	}
	// TODO: create tests
	if !handler.isFirstDone {
		handler.isFirstDone = true
		handler.nextItime = time.Now().Add(time.Duration(handler.ITimeout))
		return nil
	}

	if !handler.isTimeToSendSpecial() {
		return nil
	}

	rv := handler.SpecialJunk.GeneratePackets()
	handler.nextItime = time.Now().Add(time.Duration(handler.ITimeout))

	return rv
}

func (handler *SpecialHandshakeHandler) isTimeToSendSpecial() bool {
	return time.Now().After(handler.nextItime)
}

func (handler *SpecialHandshakeHandler) GenerateControlledJunk() [][]byte {
	if !handler.ControlledJunk.IsDefined() {
		return nil
	}

	return handler.ControlledJunk.GeneratePackets()
}

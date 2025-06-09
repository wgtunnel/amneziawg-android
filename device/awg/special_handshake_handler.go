package awg

import (
	"errors"
	"time"
)

type SpecialHandshakeHandler struct {
	SpecialJunk    TagJunkGeneratorHandler
	ControlledJunk TagJunkGeneratorHandler

	nextItime time.Time
	ITimeout  time.Duration // seconds
	// TODO: maybe atomic?
	PacketCounter uint64
	IsSet         bool
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
	// TODO: distiungish between first and the rest of the packets
	if !handler.isTimeToSendSpecial() {
		return nil
	}

	rv := handler.SpecialJunk.Generate()

	handler.nextItime = time.Now().Add(time.Duration(handler.ITimeout))

	return rv
}

func (handler *SpecialHandshakeHandler) isTimeToSendSpecial() bool {
	return time.Now().After(handler.nextItime)
}

func (handler *SpecialHandshakeHandler) PrepareControlledJunk() [][]byte {
	return handler.ControlledJunk.Generate()
}

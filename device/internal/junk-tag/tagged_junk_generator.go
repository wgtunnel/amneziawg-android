package junktag

import (
	"fmt"
	"strconv"
)

type TaggedJunkGenerator struct {
	name       string
	packetSize int
	generators []Generator
}

func newTagedJunkGenerator(name string, size int) TaggedJunkGenerator {
	return TaggedJunkGenerator{name: name, generators: make([]Generator, size)}
}

func (tg *TaggedJunkGenerator) append(generator Generator) {
	tg.generators = append(tg.generators, generator)
	tg.packetSize += generator.Size()
}

func (tg *TaggedJunkGenerator) generate() []byte {
	packet := make([]byte, 0, tg.packetSize)
	for _, generator := range tg.generators {
		packet = append(packet, generator.Generate()...)
	}

	return packet
}

func (t *TaggedJunkGenerator) nameIndex() (int, error) {
	if len(t.name) != 2 {
		return 0, fmt.Errorf("name must be 2 character long: %s", t.name)
	}

	index, err := strconv.Atoi(t.name[1:2])
	if err != nil {
		return 0, fmt.Errorf("name should be 2 char long: %w", err)
	}
	return index, nil
}
